# 项目架构

## 类依赖关系（谁调谁）

```
DemoApplication.main()
  └── AppRunner.run()
        ├── WeChatBotService.start() ───────────────────────┐
        │     ├── SessionManager.load() / save() / clear()   │ 会话持久化
        │     ├── ILinkClient (iLink SDK)                    │ 微信接入
        │     ├── imageBuffers (List<ImageEntry>)            │ 多图缓存（最多5张）
        │     ├── voiceMode (ConcurrentHashMap)              │ 语音模式开关
        │     ├── ImageService.recognizeImage()              │ 识图
        │     ├── ImageService.transformImage()              │ 图生图（多图）
        │     ├── ImageService.generateImage()               │ 文生图
        │     ├── VoiceService.textToSpeech()                │ TTS 语音合成
        │     ├── MemoryService.setFact() / getFormattedFacts()│ 记忆持久化
        │     ├── ChatService.chat() / getHistory()          │ 对话+历史
        │     ├── CommandRouter.hasCommand()                 │ 命令预判
        │     └── CommandRouter.route() ───────────────────┐ │
        │                                                  ▼ ▼
        └── CommandExecutor.start()                CommandRouter
              └── Command.execute()                    ├── ChatService.getHistory()
                                                       │         .addHistory()
共享同一套 Command 实现                                  │         .getLastUserQuestion()
                                                       ├── LlmService.chat()
        ┌──────────────────────────────────────┐       │     └── DashScope API
        ▼             ▼            ▼           ▼       │
  WeatherCommand  HelpCommand  VersionCommand ...      │
        │                                              │
        └── WeatherService ──→ 心知天气 API            │
             .getWeather()                             │
             .getForecastMulti()                       │
                                                       │
                                                ImageService
                                                  ├── recognizeImage()
                                                  │     └── qwen-vl-max API
                                                  ├── generateImage()
                                                  │     └── wan2.6-t2i API
                                                  └── transformImage()
                                                        └── wan2.6-image API

                                                VoiceService
                                                  └── textToSpeech()
                                                        └── cosyvoice-v3-flash API

                                                MemoryService
                                                  ├── setFact()       → 文件持久化
                                                  └── getFormattedFacts() → 拼接提示词
```

---

## 启动流程

```
DemoApplication.main()
  → SpringApplication.run()
  → AppRunner.run()
       ├── WeChatBotService.start()
       │     ├── SessionManager.load()          // 检查历史会话
       │     │     ├── 有 → promptAutoLogin() 3秒倒计时
       │     │     ├── 超时 → startWithSession() → loginContext()  跳过扫码
       │     │     └── 按Enter → startWithQR() → executeLogin()   重新扫码
       │     │
       │     └── 登录成功 → HeartbeatService 启动 → 等待消息
       │
       └── CommandExecutor.start()              // CLI 交互（不走LLM）
             → Scanner 循环 → 匹配命令 → Command.execute()
```

---

## 微信消息处理完整流程

### handleMessage 入口

```
WeChatBotService.handleMessage(msg)
  ├── extractVoice() 有语音? → handleVoiceMessage()
  │     └── voiceItem.getText() → iLink自动转写 → CommandRouter.route()
  │
  ├── extractImage() 有图片? → handleImageMessage()
  │     ├── [有文字] → ImageService.recognizeImage() → addImageToBuffer()
  │     └── [无文字] → ImageService.recognizeImage() → addImageToBuffer()
  │                    → extractFirstSentence() 短摘要 → 提示可继续发图
  │
  └── 纯文字 → 检查命令
        ├── hasCommand(firstWord)? → clearImageBuffer() → route()
        ├── "开启语音"/"关闭语音"? → voiceMode 切换
        │
        ├── imageBuffers 有缓存?
        │     ├── isRefreshRequest? → 重新识图
        │     └── buildImageContext(多图) → route()
        │
        └── 无缓存 → route()
```

### 回复发送

```
sendReply(fromUserId, result, images)
  ├── null/空 → "抱歉，我没理解你的意思"
  │
  ├── "IMG_GEN:" →
  │     ├── images 有数据? → ImageService.transformImage(images, prompt)  // 图生图
  │     └── 无数据 → ImageService.generateImage(prompt)                    // 文生图
  │     → client.sendImage() → 空caption
  │
  └── 文字 →
        ├── client.sendText()
        └── voiceMode[userId]=true? → VoiceService.textToSpeechMp3()
              → client.sendFile(fromUserId, bytes, "语音.mp3", "")  语音文件
```

---

## 多图支持

### 数据结构

```java
// 替代旧的 imageBuffer + imageDesc + imageBufferTime
ConcurrentHashMap<String, List<ImageEntry>> imageBuffers
MAX_IMAGES = 5                          // 最多5张
IMAGE_BUFFER_TTL = 5 * 60 * 1000        // 5分钟过期

static class ImageEntry {
    byte[] bytes;      // 图片字节（用于刷新/图生图）
    String desc;       // 完整描述（用于LLM上下文）
    long timestamp;    // 过期时间
}
```

### 多图流程

```
用户发图1 → addImageToBuffer() → "图片识别 1/5: ..."
用户发图2 → addImageToBuffer() → "图片识别 2/5: ..."
用户发图3 → addImageToBuffer() → "图片识别 3/5: ..."
用户: "合并这三张图"
  → buildImageContext(3张图) → CommandRouter.route()
  → LLM 判断为 generate_image
  → sendReply 检测到 IMG_GEN:
  → ImageService.transformImage(3张图bytes, prompt) → wan2.6-image
  → client.sendImage()
```

### 图片上下文拼接

```java
buildImageContext(images, text):
  单图: "用户发送了一张图片，内容：{desc}\n\n用户说：{text}"
  多图: "用户发送了3张图片：
        图片1：{desc1}
        图片2：{desc2}
        图片3：{desc3}
        \n\n用户说：{text}"
```

---

## 语音功能

### 语音接收

```
用户发语音气泡
  → extractVoice() 检测 VoiceItem
  → voiceItem.getText() → iLink SDK 自动转写文字
  → CommandRouter.route(text) → 正常处理
```

### 语音发送

```
用户: "开启语音" → voiceMode[userId] = true
  → 后续所有文字回复自动附带 TTS 语音文件

用户: "关闭语音" → voiceMode[userId] 移除
  → 仅回复文字

TTS流程:
  VoiceService.textToSpeechMp3(text)
    → HttpSpeechSynthesizer.callAndReturnAudio()
    → cosyvoice-v3-flash 模型
    → format("mp3"), sampleRate(24000)
    → ByteBuffer → byte[]
    → client.sendFile(fromUserId, bytes, "语音.mp3", "")
    → 微信中以文件形式发送，点开即播，不走语音气泡通道
```

### 语音格式说明

微信语音气泡仅支持 SILK 格式，其他格式（WAV/MP3）无法在气泡中播放。为避免兼容性问题及封号风险，采用**文件模式**发送语音：

```
TTS 输出 MP3 → client.sendFile() → 微信文件消息 → 点开播放
```

对比语音气泡：
| | 语音气泡 (sendVoice) | 语音文件 (sendFile) |
|--|---------------------|-------------------|
| 格式 | 仅 SILK | 任意格式 |
| 封号风险 | 高（触碰微信语音协议） | 无（普通文件） |
| 播放方式 | 自动出现气泡 | 点开文件播放 |

---

## 对话上下文压缩

ChatService 使用 Redis 存储 + Token 阈值判断 + 滑动窗口兜底 + 对话摘要四层机制管理上下文。

### 四层架构

```
Layer 1: Redis 存储
  → chat:{userId} → JSON 历史列表，24h TTL 自动过期
  → 解决沉睡用户堆积，Redis 不可用时回退内存

Layer 2: Token 阈值判断（主要防线）
  → 用 DashScope Tokenizer 精确计算 token 数
  → 接近 100K（qwen-plus 最大 128K）时触发压缩
  → 配置: llm.token-threshold: 100000

Layer 3: 滑动窗口（兜底防线）
  → 消息数超过 50 条时触发压缩
  → 配置: llm.max-history: 50

Layer 4: 对话摘要（压缩手段）
  → 取最旧的一半消息 → LLM 压缩为 80 字摘要
  → 替换为 system 消息 → 保留最近 10 条完整消息
```

### 压缩流程

```
触发条件: tokens > 100K 或 消息数 > 50
  ↓
1. 取最旧的一半消息
2. 拼接为文本 → 调 LLM 生成 80 字以内摘要
3. 替换为一条 system 消息: {"role":"system","content":"历史摘要：xxx"}
4. 保留最近 10 条完整消息 + 新摘要
5. 保存回 Redis，刷新 24h TTL

示例:
  对话: "用户：杭州天气.. 助手：杭州28°... 用户：画只猫.. 助手：生成成功.."
  摘要: "历史摘要：用户查了杭州天气(28°多云)，让画了猫"
  
  压缩后: [摘要] + 最近10条完整消息
```

### 优势

- 丢的是原始对话原文，保留的是核心信息
- 模型能记住早期讨论（名字、偏好、重要对话）
- token 消耗远低于保留全部原文
- 对话越长，越早期的信息越浓缩，但不会完全丢失

---

## 图片生成：文生图 vs 图生图

```
sendReply 检测到 IMG_GEN:
  ├── images 有数据（图片缓存存在）
  │     → ImageService.transformImage(List<byte[]>, prompt)
  │     → wan2.6-image 模型
  │     → 原图作为参考 + 文字作为风格指令
  │     → 保留原图内容，按指令变换
  │
  └── images 无数据（从零生成）
        → ImageService.generateImage(prompt)
        → wan2.6-t2i 模型
        → 纯文字提示词生成
```

---

## CLI 处理流程

```
CommandExecutor.start()
  → Scanner 循环
  → 读一行 → cmdName + args
  → cmdName == "exit" → 退出
  → commands.get(cmdName) → cmd.execute(args)
```

CLI 和 Bot 共用 Command 接口，但 CLI 不走 LLM，只支持精确命令匹配。

---

## 天气查询流程

```
用户: "杭州天气"
  → CommandRouter → LlmService → function_call("weather", {"city":"杭州"})
  → WeatherCommand.execute(["杭州"])   // days=1
  → WeatherService.getWeather("杭州")
      └── GET /v3/weather/now.json
      └── 返回: text, temperature, feels_like, humidity,
               wind_direction, wind_speed, wind_scale,
               visibility, pressure

用户: "杭州未来五天"
  → function_call("weather", {"city":"杭州","days":7})
  → getForecastMulti("杭州", apiDays=7, showDays=5)
      └── GET /v3/weather/daily.json?days=7
      └── 格式化: "07-18 明天: 晴 28~38°C"
```

### 天气智能分流

```
"杭州多少度" / "杭州明天" / "纽约天气"
  → LLM 判断是实时/预报 → function_call(weather) → 心知天气 API

"杭州热不热" / "杭州昨天天气" / "杭州夏天一般多少度"
  → LLM 判断是主观/历史/常识 → 千问直接回答（不调API）
```

---

## SDK 接口总结

### iLink SDK（微信接入）

| 功能 | 方法 |
|------|------|
| 扫码登录 | `ILinkClient.executeLogin()` |
| 会话恢复 | `.loginContext(ctx)` |
| 导出会话 | `.exportResumeContext()` |
| 接收消息 | `OnMessageListener.onMessages()` |
| 发文字 | `sendText(toUserId, text)` |
| 发图片 | `sendImage(toUserId, bytes, name, caption)` |
| 发语音文件 | `sendFile(toUserId, bytes, name, caption)` |
| 发语音气泡 | `sendVoice(toUserId, bytes, name, playTimeMs, sampleRate)` |
| 下载图片 | `downloadImageFromMessageItem(item)` |
| 下载语音 | `downloadVoiceFromMessageItem(item)` |

### DashScope SDK（大模型）

| 功能 | 类 | 方法 | 模型 |
|------|-----|------|------|
| 文字对话 | Generation | `call(GenerationParam)` | qwen-plus |
| 识图 | MultiModalConversation | `call(MultiModalConversationParam)` | qwen-vl-max |
| 文生图 | ImageGeneration | `call(ImageGenerationParam)` | wan2.6-t2i |
| 图生图 | ImageGeneration | `call(ImageGenerationParam)` | wan2.6-image |
| 语音合成 | HttpSpeechSynthesizer | `callAndReturnAudio(param)` | cosyvoice-v3-flash |

---

## 模型列表

| 模型 | 用途 | SDK 类 |
|------|------|--------|
| qwen-plus | 文字对话 + Function Calling | Generation |
| qwen-vl-max | 图片识别 | MultiModalConversation |
| wan2.6-t2i | 文生图（从零生成） | ImageGeneration |
| wan2.6-image | 图生图（参考原图变换） | ImageGeneration |
| cosyvoice-v3-flash | TTS 语音合成 | HttpSpeechSynthesizer |

---

## 配置与环境变量

| 变量名 | 用途 |
|--------|------|
| `SENIVERSE_API_KEY` | 心知天气 |
| `A_BAILIAN_API_KEY` | 阿里云百炼千问 |
| `llm.model` | 模型名（qwen-plus） |
| `llm.max-history` | 对话历史条数（默认20） |

---

## 关键设计

1. **命令复用**：CLI 和微信 Bot 共用 Command 接口
2. **Function Calling**：一次 LLM API 完成意图判断 + 执行命令/聊天/生图
3. **天气智能分流**：实时/未来 → 心知天气；主观/历史 → 千问直接答
4. **多图缓存**：最大5张、5分钟过期，支持"合并这两张图"等指令
5. **图生图自动切换**：有原图缓存时自动用 wan2.6-image，无原图用 wan2.6-t2i
6. **LLM 上下文感知**：去掉关键词匹配，图片描述拼接用户消息，LLM 自行判断意图
7. **命令清除缓存**：匹配到命令时自动清除图片缓存，避免上下文干扰
8. **语音模式**：iLink 自动转写收语音 + DashScope TTS 发语音
9. **会话持久化**：LoginContext 序列化到 JSON，重启免扫码，3秒内按Enter切换
10. **四层上下文管理**：Redis 存储 + Token 阈值判断 + 滑动窗口兜底 + 对话摘要压缩。Redis 24h TTL 自动过期沉睡用户，DashScope Tokenizer 精确估算 token，双重阈值（100K tokens / 50条消息）触发压缩。
11. **对话历史**：ChatService 管理，Redis 持久化，回退到内存
12. **用户记忆**：MemoryService 持久化用户个人信息到 JSON 文件，重启不丢。LLM 通过 `remember_fact` 工具保存，每次对话自动加载到 system prompt。
