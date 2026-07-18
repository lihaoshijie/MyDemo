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
        └── voiceMode[userId]=true? → VoiceService.textToSpeech()
              → client.sendVoice()  语音播报
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
  → 后续所有文字回复自动附带 TTS 语音播报

用户: "关闭语音" → voiceMode[userId] 移除
  → 仅回复文字

TTS流程:
  VoiceService.textToSpeech(text)
    → HttpSpeechSynthesizer.callAndReturnAudio()
    → cosyvoice-v3-flash 模型
    → ByteBuffer → byte[]
    → client.sendVoice(toUserId, bytes, "voice.wav", 0, 24000)
```

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
| 发语音 | `sendVoice(toUserId, bytes, name, playTimeMs, sampleRate)` |
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
10. **对话历史**：ChatService 内存存储，每用户最多 20 条
11. **兜底澄清**：结果为空时回复"我没理解你的意思"
