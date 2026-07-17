# 项目架构

## 整体结构

```
demo
├── DemoApplication.java          # 启动入口
├── bot/                          # 微信机器人层
│   ├── WeChatBotService.java     # iLink 客户端管理（登录、收消息、回复）
│   └── CommandRouter.java        # 消息路由（直接命令 → function calling → 聊天）
├── cli/
│   └── CommandExecutor.java      # 命令行交互
├── command/                      # 命令层（CLI + 微信共用）
│   ├── Command.java              # 命令接口
│   ├── HelpCommand.java
│   ├── WeatherCommand.java       # 支持实时/多天预报（3/7/15天）
│   ├── StatusCommand.java
│   ├── VersionCommand.java
│   └── ExitCommand.java
├── service/                      # 服务层
│   ├── WeatherService.java       # 心知天气 API（实时 + 多天预报）
│   ├── LlmService.java           # DashScope SDK（千问 function calling）
│   └── ChatService.java          # 对话历史管理（纯历史存储）
├── model/                        # 数据模型
├── entity/                       # 数据库实体
├── mapper/                       # MyBatis Mapper
├── exception/                    # 异常体系
├── controller/                   # Web API
└── resources/
    └── application.yaml          # 配置文件
```

## 核心流程

### 启动流程
```
DemoApplication.main()
  └── AppRunner.run()
      ├── WeChatBotService.start()     # 微信 Bot 扫码登录
      └── CommandExecutor.start()      # CLI 交互
```

### 消息处理流程
```
微信消息
  ↓
WeChatBotService (收到消息 → 提取文本)
  ↓
CommandRouter.route("杭州天气", "userId")
  ↓
├── 直接匹配命令成功? → 执行命令 → 回复
│   (help / weather 北京 / exit 等)
│
└── 未匹配 → LlmService.chat() 带 tools（1次 API 调用）
       ↓
   千问根据语义判断:
   ├── function_call("weather", {"city":"杭州","days":3})
   │   → WeatherCommand → WeatherService → 心知天气 API → 回复
   │
   ├── function_call("status"/"help"/"version")
   │   → 执行对应命令 → 回复
   │
   └── text（无 function_call）
       → ChatService 保存历史 → 直接回复
```

### 天气智能分流
```
用户: "现在杭州多少度"  → 千问判断: 实时天气 → 调心知天气 API（精确数据）
用户: "纽约的天气"      → 千问判断: 实时天气 → 调心知天气 API（支持全球城市）
用户: "杭州明天热不热"  → 千问判断: 主观感受 → 千问直接回答
用户: "杭州昨天天气"    → 千问判断: 历史天气 → 千问直接回答（心知不支持）
用户: "杭州未来五天"    → 千问判断: 多天预报 → 调心知天气 API（days=7）
用户: "适合去杭州玩吗"  → 千问判断: 综合判断 → 千问直接回答
```

## 模块详解

### command/ — 命令层
```
Command (接口)
  ├── HelpCommand     → 显示帮助
  ├── WeatherCommand  → 查天气（支持实时 + 3/7/15天预报）
  ├── StatusCommand   → 系统状态
  ├── VersionCommand  → 版本信息
  └── ExitCommand     → 仅限 CLI
```

### service/ — 服务层
| 类 | 职责 |
|------|------|
| WeatherService | 调心知天气 API（实时 + daily 多天预报） |
| LlmService | DashScope SDK 千问 API（function calling + 对话） |
| ChatService | 管理用户对话历史（最多 20 条，纯存储） |

### bot/ — 微信机器人
| 类 | 职责 |
|------|------|
| WeChatBotService | iLink 客户端生命周期（扫码登录、消息监听、回复） |
| CommandRouter | 消息路由（直接命令 → function calling → 聊天） |

### WeatherCommand 支持的天数
| 用户说 | days 参数 | API 调用 | 输出 |
|--------|----------|---------|------|
| 杭州天气 | 1 | /v3/weather/now.json | 实时天气 |
| 纽约天气 | 1 | /v3/weather/now.json | 实时天气（全球城市） |
| 杭州明天 | 3 | /v3/weather/daily.json?days=3 | 3天预报 |
| 杭州未来五天 | 7 | /v3/weather/daily.json?days=7 | 7天预报 |
| 杭州未来十天 | 15 | /v3/weather/daily.json?days=15 | 15天预报 |

### exception/ — 异常体系
```
BusinessException
  ├── MissingParameterException
  ├── InvalidParameterException
  ├── CityNotFoundException
  ├── WeatherApiException
  └── VersionApiException
```

## SDK 依赖

| SDK | 用途 | 主要类 |
|-----|------|--------|
| wechat-ilink-sdk-java | 微信接入 | ILinkClient, OnMessageListener, WeixinMessage |
| dashscope-sdk-java | 千问大模型 | Generation, GenerationParam, ToolFunction, FunctionDefinition |

## 配置与环境变量

### application.yaml
```yaml
spring.datasource     # MySQL
seniverse.api.key     # 心知天气 API
llm.api-key           # 千问 API Key
llm.model             # 模型名称（qwen-plus）
llm.base-url          # API 地址
llm.system-prompt     # 系统提示词（含天气分流逻辑）
```

### 环境变量
| 变量名 | 用途 |
|--------|------|
| SENIVERSE_API_KEY | 心知天气 |
| A_BAILIAN_API_KEY | 阿里云百炼千问 |

## 关键设计

1. **命令复用**：CLI 和微信共用 Command 接口
2. **function calling**：一次 API 完成意图判断+执行/聊天
3. **天气智能分流**：实时/未来 → 调 API；主观/历史 → 千问直接答
4. **对话历史**：每个用户独立维护最近 20 条
5. **iLink 心跳**：SDK 内置心跳自动轮询消息
6. **DashScope SDK**：替代手动 WebClient，自动处理序列化/异常/API Key
