# 项目架构

## 类依赖关系（谁调谁）

```
DemoApplication.main()
  └── AppRunner.run()
        ├── WeChatBotService.start() ─────────────────────┐
        │     ├── SessionManager.load() / save() / clear() │ 静态工具
        │     ├── ILinkClient (iLink SDK)                  │ 微信接入
        │     ├── imageBuffer / imageDesc                  │ 图片缓存+描述缓存
        │     ├── ImageService.recognizeImage()            │ 识图（仅首次+刷新）
        │     ├── ImageService.generateImage()             │ 生图
        │     ├── ChatService.chat()                       │ 图片描述上下文对话
        │     └── CommandRouter.route() ─────────────────┐ │
        │                                                ▼ ▼
        └── CommandExecutor.start()              CommandRouter
              └── Command.execute()                    ├── ChatService.getHistory()
                                                       │         .addHistory()
共享同一套 Command 实现                                  │
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
                                                  └── generateImage()
                                                        └── wan2.6-t2i API
```

---

## 启动流程

```
DemoApplication.main()
  → SpringApplication.run()
  → AppRunner.run()                                    // CommandLineRunner
       ├── WeChatBotService.start()                    // 启动微信 Bot
       │     ├── SessionManager.load()                 // 检查历史会话
       │     │     ├── 有 → promptAutoLogin() 3秒倒计时
       │     │     │     ├── 超时 → startWithSession() → loginContext()
       │     │     │     └── 按Enter → startWithQR() → executeLogin()
       │     │     └── 无 → startWithQR() → executeLogin()
       │     │           → 扫码成功 → onLoginSuccess()
       │     │           → SessionManager.save(ctx)     // 保存凭证
       │     │           → HeartbeatService 启动
       │     │
       │     └── [等待消息...]
       │
       └── CommandExecutor.start()                      // 启动 CLI
             → Scanner 循环读取键盘输入
             → 匹配命令 → Command.execute()
```

---

## 微信消息处理完整流程

### 主流程

```
微信服务器
  ↓
HeartbeatService (每5s触发)
  ↓
ILinkClient.pollAndDispatchMessages()          // SDK 内部长轮询
  ↓
OnMessageListener.onMessages(List<WeixinMessage>)
  ↓
WeChatBotService.handleMessages()
  ↓
WeChatBotService.handleMessage(msg)
  ├── 提取 from_user_id = msg.getFrom_user_id()
  ├── 遍历 msg.getItem_list()
  │
  ├── [图片消息] → handleImageMessage()
  │     ├── client.downloadImageFromMessageItem(imageItem) → byte[]
  │     ├── [有文字] → 直接识图
  │     │     └── ImageService.recognizeImage(imageBytes, text)
  │     │           → qwen-vl-max → return 文字描述
  │     └── [无文字] → 缓存到 imageBuffer[userId]
  │           → 回复 "已收到图片，请告诉我你想了解什么"
  │
  ├── [文字消息] → 提取文本
  │     ├── [imageBuffer 有缓存图片] ←── 图片与文字分离的解决方案
  │     │     ├── 缓存取出 → imageBuffer.remove(userId)
  │     │     ├── ImageService.recognizeImage(缓存图片, text)
  │     │     └── sendReply → client.sendText()
  │     │
  │     └── [无缓存图片] → CommandRouter.route(text, userId)
              │
              ├── 步骤1: 直接命令匹配
              │     ├── 输入按空格分词 → cmdName + args
              │     ├── commands.get(cmdName) 查找
              │     └── [匹配] → executeCommand(cmd, cmdName, args)
              │           └── cmd.execute(args)
              │           └── return 结果
              │
              └── 步骤2: LLM function calling（未匹配时）
                    ├── ChatService.getHistory(userId)  → 对话历史
                    ├── LlmService.chat(text, tools, history)
                    │     └── Generation.call()        // DashScope SDK
                    │           └── qwen-plus API（1次调用）
                    │                 └── return LlmResult
                    │
                    ├── [function_call: weather]
                    │     └── extractArgs → {city, days}
                    │     └── WeatherCommand.execute() → WeatherService
                    │     └── ChatService.addHistory()
                    │     └── return 天气信息
                    │
                    ├── [function_call: generate_image]
                    │     └── extractPrompt → prompt
                    │     └── return "IMG_GEN:" + prompt
                    │           → WeChatBotService.sendReply()
                    │           → ImageService.generateImage()
                    │           → client.sendImage()
                    │
                    ├── [function_call: help/status/version]
                    │     └── cmd.execute() → return 结果
                    │
                    └── [text: 普通对话]
                          └── ChatService.addHistory()
                          └── return 千问回复文本
```

### 回复发送

```
WeChatBotService.sendReply(fromUserId, result)
  ├── result == null → 忽略
  ├── result.startsWith("IMG_GEN:")   → ImageService.generateImage()
  │     └── ImageGeneration.call()    → wan2.6-t2i
  │     └── 下载图片 URL → byte[]
  │     └── client.sendImage(fromUserId, bytes, "image.png", prompt)
  └── 其他                            → client.sendText(fromUserId, result)
```

---

## CLI 处理流程

```
CommandExecutor.start()
  → Scanner 循环
  → 读一行 → 按空格分词 → cmdName + args
  → cmdName == "exit" → 退出循环
  → commands.get(cmdName) → cmd.execute(args)
  → 异常捕获 → BusinessException 各子类
```

CLI 和 Bot 共用同一套 Command 实现，但 CLI **不走 LLM**，只支持精确命令匹配。

---

## 天气查询流程

### 实时天气

```
用户: "杭州天气"
  → CommandRouter → LlmService → function_call("weather", {"city":"杭州"})
  → WeatherCommand.execute(["杭州"])            // days=1
  → WeatherService.getWeather("杭州")
      └── GET /v3/weather/now.json?location=杭州
      └── 解析: text, temperature, feels_like, humidity,
               wind_direction, wind_speed, wind_scale,
               visibility, pressure
      └── return WeatherResponse
  → CommandRouter 格式化输出
```

### 多天预报

```
用户: "杭州未来五天"
  → CommandRouter → LlmService → function_call("weather", {"city":"杭州","days":7})
  → WeatherCommand.execute(["杭州", "7"])        // days=7
  → days<=3 → apiDays=3, days<=7 → apiDays=7, else → apiDays=15
  → WeatherService.getForecastMulti("杭州", apiDays=7, showDays=5)
      └── GET /v3/weather/daily.json?location=杭州&days=7
      └── 解析 daily[] 数组，取前 showDays 条
      └── 格式化: "07-18 明天: 晴 28~38°C"
```

### 天气智能分流

```
用户消息
  ├── "杭州多少度" / "杭州明天" / "纽约天气"
  │     → LLM 判断是实时/预报 → function_call(weather) → 心知天气 API
  │
  └── "杭州热不热" / "杭州昨天天气" / "杭州夏天一般多少度"
        → LLM 判断是主观/历史/常识 → 千问直接回答（不调 API）
```

---

## 图片处理流程

### 问题：微信图片和文字分开发

微信协议层将图片消息和文本消息拆成两条独立的 `WeixinMessage` 推送，无法在一条消息里同时收到图片和文字。

### 解决方案：图片描述缓存 + 多轮问答

```
ConcurrentHashMap<String, byte[]>  imageBuffer      // userId → 图片字节（用于刷新）
ConcurrentHashMap<String, Long>    imageBufferTime  // userId → 时间戳
ConcurrentHashMap<String, String>  imageDesc        // userId → 图片描述文本
IMAGE_BUFFER_TTL = 5 * 60 * 1000                    // 5分钟有效期
```

### 核心思路

```
发图片 → AI读图 → 存描述到 imageDesc + 会话记录 → 缓存图片字节
提问   → 从 imageDesc 取描述 → 拼入上下文给 LLM → 基于描述回答（不调识图API）
重新看 → 重新读图 → 更新 imageDesc + 会话记录
无关   → 清除所有缓存
```

### 场景1：用户发图片（无文字）

```
handleImageMessage()
  ├── downloadImageFromMessageItem → byte[]
  ├── ImageService.recognizeImage(bytes, "请详细描述图片内容")
  │     → qwen-vl-max → return 文字描述
  ├── imageBuffer.put(userId, bytes)        // 缓存图片字节（用于刷新）
  ├── imageDesc.put(userId, description)    // 缓存描述文本（用于回答）
  ├── ChatService.addHistory(userId, "[用户发送了图片]", description)
  └── sendReply → "【图片内容】\n\n{description}\n\n有其他问题可以继续问我"
```

### 场景2：用户针对图片提问（有缓存）

```
handleMessage() 检测到 imageBuffer 有缓存
  │
  ├── 是刷新请求（仔细看看/重新看看）?
  │     → ImageService.recognizeImage(缓存bytes, "重新详细描述")
  │     → 更新 imageDesc + ChatService
  │     → sendReply → "【图片内容已更新】\n\n{新描述}"
  │
  ├── 是图片相关提问（isImageQuestion=true）?
  │     → 从 imageDesc 取出描述
  │     → ChatService.chat(userId, "用户之前发送了一张图片，内容：{desc}，现在问：{text}")
  │     → LLM 基于描述回答（不调识图API，速度快）
  │     → sendReply
  │
  └── 无关话题 → 清除 imageBuffer + imageDesc → 正常 CommandRouter 流程
```

### 场景3：只发图片不发文字

```
→ 立即识图 → 存描述 → 回复描述内容
→ 5分钟内用户可以继续提问
→ 5分钟后缓存过期自动清除
```

### isImageQuestion 关键词判断

```java
// 图片相关关键词
"图", "这", "那", "它", "什么", "颜色", "多少", "描述", "看看",
"文案", "写", "配速", "健身", "运动", "屏幕", "显示", "读", "拍"
// 短文本（<15字）也认为是图片相关
```

### isRefreshRequest 关键词判断

```java
// 刷新关键词
"仔细看看", "再看看", "重新看看", "再看一下", "仔细观察",
"重新识别", "重新描述", "再描述", "重新读", "再读"
```

### 图片生成（用户说"画XX"）

```
用户: "画一只猫"
  ↓
CommandRouter.route("画一只猫", userId)
  → commands.get("画一只猫") → null（不匹配）
  → LlmService.chat() with tools
      → function_call("generate_image", {"prompt":"一只可爱的猫"})
  → CommandRouter 返回 "IMG_GEN:一只可爱的猫"
  ↓
WeChatBotService.sendReply()
  → 检测到 "IMG_GEN:" 前缀
  → ImageService.generateImage("一只可爱的猫")
      ├── ImageGeneration.call()
      │     ├── model: wan2.6-t2i
      │     ├── prompt: "一只可爱的猫"
      │     ├── n: 1, size: 1024*1024
      │     └── return 图片 URL
      ├── downloadImage(url) → 下载 → byte[]
      └── return byte[]
  → client.sendImage(fromUserId, bytes, "image.png", "一只可爱的猫")
```

---

## SDK 接口总结

| 功能 | SDK 类 | 方法 |
|------|--------|------|
| 登录（扫码） | ILinkClient | `executeLogin()` |
| 登录（恢复） | ILinkClient | `.loginContext(ctx)` |
| 保存会话 | ILinkClient | `exportResumeContext()` |
| 接收消息 | ILinkClient | OnMessageListener → `onMessages()` |
| 发文字 | ILinkClient | `sendText(toUserId, text)` |
| 发图片 | ILinkClient | `sendImage(toUserId, bytes, name, caption)` |
| 下载图片 | ILinkClient | `downloadImageFromMessageItem(item)` |
| 文字对话 | Generation | `call(GenerationParam)` |
| 识图 | MultiModalConversation | `call(MultiModalConversationParam)` |
| 生图 | ImageGeneration | `call(ImageGenerationParam)` |

---

## 模型列表

| 模型 | 用途 | SDK 类 |
|------|------|--------|
| qwen-plus | 文字对话 + function calling | Generation |
| qwen-vl-max | 图片识别 | MultiModalConversation |
| wan2.6-t2i | 图片生成 | ImageGeneration |

---

## 配置与环境变量

| 变量名 | 用途 |
|--------|------|
| `SENIVERSE_API_KEY` | 心知天气 API |
| `A_BAILIAN_API_KEY` | 阿里云百炼千问 |
| `llm.model` | 模型名（qwen-plus） |
| `llm.max-history` | 对话历史条数（默认20） |

---

## 关键设计

1. **命令复用**：CLI（CommandExecutor）和微信 Bot（CommandRouter）共用 Command 接口
2. **function calling**：一次 LLM API 完成意图判断 + 执行命令/聊天/生图
3. **天气智能分流**：实时/未来 → 心知天气；主观/历史 → 千问直接答
4. **图片描述缓存**：发图时立即识图存描述到 `imageDesc`，后续提问基于描述回答（不调识图API），"重新看看"才重新调API
5. **图片多轮问答**：`isImageQuestion` 关键词判断是否针对图片提问，`isRefreshRequest` 判断是否刷新
6. **IMG_GEN 标记**：LLM 判断要生图时返回特殊标记，WeChatBotService 检测后调生图
7. **会话持久化**：SessionManager 序列化 LoginContext 到 JSON，重启免扫码
8. **对话历史**：ChatService 内存存储，每用户最多 20 条，含图片描述记录
