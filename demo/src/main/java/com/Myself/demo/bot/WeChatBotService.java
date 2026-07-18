package com.Myself.demo.bot;

import com.Myself.demo.service.ImageService;
import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.core.config.ILinkConfig;
import com.github.wechat.ilink.sdk.core.listener.OnLoginListener;
import com.github.wechat.ilink.sdk.core.listener.OnMessageListener;
import com.github.wechat.ilink.sdk.core.login.LoginContext;
import com.github.wechat.ilink.sdk.core.model.MessageItem;
import com.github.wechat.ilink.sdk.core.model.WeixinMessage;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class WeChatBotService {

    @Autowired
    private CommandRouter commandRouter;

    @Autowired
    private ImageService imageService;

    @Autowired
    private com.Myself.demo.service.ChatService chatService;

    private ILinkClient client;
    private final ConcurrentHashMap<String, byte[]> imageBuffer = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> imageBufferTime = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> imageDesc = new ConcurrentHashMap<>();
    private static final long IMAGE_BUFFER_TTL = 5 * 60 * 1000; // 5分钟

    public void start() {
        ILinkConfig config = ILinkConfig.builder()
                .connectTimeoutMs(35000)
                .readTimeoutMs(35000)
                .writeTimeoutMs(35000)
                .heartbeatEnabled(true)
                .heartbeatIntervalMs(5000)
                .channelVersion("1.0.0")
                .build();

        LoginContext savedContext = SessionManager.load();

        if (savedContext != null) {
            if (promptAutoLogin()) {
                startWithSession(config, savedContext);
            } else {
                SessionManager.clear();
                startWithQR(config);
            }
        } else {
            startWithQR(config);
        }
    }

    private boolean promptAutoLogin() {
        System.out.println("=========================================");
        System.out.println("检测到历史登录会话，将在 3 秒后自动登录");
        System.out.println("如需切换账号，请按 Enter 键跳过自动登录");
        System.out.println("=========================================");
        try {
            if (System.in.available() > 0) {
                System.in.read(new byte[System.in.available()]);
            }
            long deadline = System.currentTimeMillis() + 3000;
            while (System.currentTimeMillis() < deadline) {
                if (System.in.available() > 0) {
                    System.in.read(new byte[System.in.available()]);
                    System.out.println("已取消自动登录，进入扫码模式");
                    return false;
                }
                Thread.sleep(100);
            }
        } catch (Exception e) {
            log.warn("等待用户输入异常", e);
        }
        return true;
    }

    private void startWithSession(ILinkConfig config, LoginContext savedContext) {
        log.info("尝试使用历史会话恢复登录...");

        client = ILinkClient.builder()
                .config(config)
                .loginContext(savedContext)
                .onMessage(new OnMessageListener() {
                    @Override
                    public void onMessages(List<WeixinMessage> messages) {
                        handleMessages(messages);
                    }
                })
                .build();

        if (client.isLoggedIn()) {
            log.info("历史会话恢复成功，已自动登录");
            System.out.println("✓ 微信 Bot 自动登录成功! 无需扫码");
        } else {
            log.warn("历史会话恢复失败，切换到扫码模式");
            SessionManager.clear();
            startWithQR(config);
        }
    }

    private void startWithQR(ILinkConfig config) {
        client = ILinkClient.builder()
                .config(config)
                .onLogin(new OnLoginListener() {
                    @Override
                    public void onLoginSuccess(LoginContext context) {
                        log.info("iLink 登录成功, botId = {}", context.getBotId());
                        System.out.println("✓ 微信 Bot 登录成功!");
                        SessionManager.save(context);
                    }

                    @Override
                    public void onLoginFailure(Throwable throwable) {
                        log.error("iLink 登录失败", throwable);
                        System.out.println("✗ 微信 Bot 登录失败: " + throwable.getMessage());
                        SessionManager.clear();
                    }
                })
                .onMessage(new OnMessageListener() {
                    @Override
                    public void onMessages(List<WeixinMessage> messages) {
                        handleMessages(messages);
                    }
                })
                .build();

        try {
            String qrCodeImgContent = client.executeLogin();
            log.info("请扫码登录");
            System.out.println("=== 微信 iLink Bot ===");
            System.out.println("请用微信扫描下方二维码登录 Bot：");
            System.out.println(qrCodeImgContent);
            System.out.println("=====================");
        } catch (Exception e) {
            log.error("启动 iLink 失败", e);
            System.out.println("✗ 微信 Bot 启动失败: " + e.getMessage());
        }
    }

    private void handleMessages(List<WeixinMessage> messages) {
        for (WeixinMessage msg : messages) {
            handleMessage(msg);
        }
    }

    private void handleMessage(WeixinMessage msg) {
        if (msg.getItem_list() == null) return;

        String fromUserId = msg.getFrom_user_id();

        MessageItem imageItem = extractImage(msg);
        if (imageItem != null) {
            handleImageMessage(fromUserId, imageItem, extractText(msg));
            return;
        }

        String text = extractText(msg);
        if (text == null || text.isEmpty()) return;

        log.info("收到微信消息 from={}, text={}", fromUserId, text);

        String firstWord = text.trim().split("\\s+")[0].toLowerCase();
        if (commandRouter.hasCommand(firstWord)) {
            clearImageBuffer(fromUserId);
            String result = commandRouter.route(text, fromUserId);
            sendReply(fromUserId, result);
            return;
        }

        byte[] bufferedImage = imageBuffer.get(fromUserId);
        Long bufferTime = imageBufferTime.get(fromUserId);

        if (bufferedImage != null && bufferTime != null
                && System.currentTimeMillis() - bufferTime < IMAGE_BUFFER_TTL) {

            imageBufferTime.put(fromUserId, System.currentTimeMillis());

            if (isRefreshRequest(text)) {
                log.info("重新识别图片, from={}", fromUserId);
                String lastQuestion = chatService.getLastUserQuestion(fromUserId);
                String reExamPrompt = buildReExamPrompt(text, lastQuestion);
                String answer = imageService.recognizeImage(bufferedImage, reExamPrompt);
                imageDesc.put(fromUserId, answer);
                chatService.addHistory(fromUserId, "[重新识别图片]", answer);
                sendReply(fromUserId, answer);
                return;
            }

            String desc = imageDesc.get(fromUserId);
            if (desc != null) {
                String modifiedText = "用户发送了一张图片，图片内容如下：" + desc
                        + "\n\n用户现在说：" + text;
                log.info("图片上下文拼接, from={}", fromUserId);
                String result = commandRouter.route(modifiedText, fromUserId);
                if (result != null && !result.isEmpty()) {
                    chatService.addHistory(fromUserId, text, result);
                }
                sendReply(fromUserId, result);
                return;
            }
        }

        clearImageBuffer(fromUserId);
        String result = commandRouter.route(text, fromUserId);
        sendReply(fromUserId, result);
    }

    private void clearImageBuffer(String userId) {
        if (imageBuffer.remove(userId) != null) {
            imageBufferTime.remove(userId);
            imageDesc.remove(userId);
            log.info("清除图片缓存, userId={}", userId);
        }
    }

    private void handleImageMessage(String fromUserId, MessageItem imageItem, String text) {
        log.info("收到图片消息 from={}, text={}", fromUserId, text);

        try {
            byte[] imageBytes = client.downloadImageFromMessageItem(imageItem);

            if (text != null && !text.isEmpty()) {
                String answer = imageService.recognizeImage(imageBytes, text);
                imageBuffer.put(fromUserId, imageBytes);
                imageBufferTime.put(fromUserId, System.currentTimeMillis());
                imageDesc.put(fromUserId, answer);
                chatService.addHistory(fromUserId, "[图片] " + text, answer);
                sendReply(fromUserId, answer);
            } else {
                String fullDesc = imageService.recognizeImage(imageBytes,
                        "请详细描述这张图片的内容，包括所有可见的文字、数字、符号、布局等细节");
                imageBuffer.put(fromUserId, imageBytes);
                imageBufferTime.put(fromUserId, System.currentTimeMillis());
                imageDesc.put(fromUserId, fullDesc);
                chatService.addHistory(fromUserId, "[用户发送了图片]", fullDesc);

                String shortDesc = fullDesc.length() > 150
                        ? fullDesc.substring(0, 150) + "..."
                        : fullDesc;
                sendReply(fromUserId, "【图片识别】\n\n" + shortDesc
                        + "\n\n可以继续提问，或要求我生成图片");
            }
        } catch (Exception e) {
            log.error("处理图片消息失败", e);
            try {
                client.sendText(fromUserId, "抱歉，图片接收失败，请稍后再试");
            } catch (Exception ex) {
                log.error("发送错误回复失败", ex);
            }
        }
    }

    private void sendReply(String fromUserId, String result) {
        if (result == null || result.isEmpty()) {
            try {
                client.sendText(fromUserId, "抱歉，我没理解你的意思，可以再说详细一点吗？");
            } catch (Exception e) {
                log.error("发送澄清回复失败", e);
            }
            return;
        }

        try {
            if (result.startsWith("IMG_GEN:")) {
                String prompt = result.substring(8);
                log.info("生成图片: {}", prompt);
                byte[] imageBytes = imageService.generateImage(prompt);
                client.sendImage(fromUserId, imageBytes, "image.png", prompt);
                log.info("图片发送成功");
            } else {
                client.sendText(fromUserId, result);
                log.info("回复消息成功: {}", result.substring(0, Math.min(50, result.length())));
            }
        } catch (Exception e) {
            log.error("回复失败", e);
            try {
                client.sendText(fromUserId, "抱歉，操作失败，请稍后再试");
            } catch (Exception ex) {
                log.error("发送错误回复失败", ex);
            }
        }
    }

    private MessageItem extractImage(WeixinMessage msg) {
        if (msg.getItem_list() == null) return null;
        for (MessageItem item : msg.getItem_list()) {
            if (item.getImage_item() != null) {
                return item;
            }
        }
        return null;
    }

    private String extractText(WeixinMessage msg) {
        if (msg.getItem_list() == null) return null;
        for (MessageItem item : msg.getItem_list()) {
            if (item.getText_item() != null) {
                return item.getText_item().getText();
            }
        }
        return null;
    }

    private boolean isRefreshRequest(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase();
        String[] keywords = {"仔细看看", "再看看", "重新看看", "再看一下", "仔细观察",
                "重新识别", "重新描述", "再描述", "仔细描述", "重新读", "再读"};
        for (String kw : keywords) {
            if (lower.contains(kw)) return true;
        }
        return false;
    }

    private String buildReExamPrompt(String userText, String lastQuestion) {
        String lower = userText.toLowerCase();
        String[] correctionKeywords = {"不是", "不对", "错了", "有误", "错误", "搞错", "错了吧"};
        boolean isCorrection = false;
        for (String kw : correctionKeywords) {
            if (lower.contains(kw)) {
                isCorrection = true;
                break;
            }
        }

        String coreQuestion = userText
                .replaceAll("你再?仔细看看", "")
                .replaceAll("你再?看看", "")
                .replaceAll("重新看看", "")
                .replaceAll("仔细观察", "")
                .trim();

        if (isCorrection) {
            return "用户指出之前的回答有误。用户说：'" + userText + "'。"
                    + "请仔细重新观察图片，准确判断用户质疑的内容是否正确，"
                    + "直接回答是或不是，并给出依据。不要输出无关描述。";
        }

        if (!coreQuestion.isEmpty()) {
            return "请仔细观察图片，只回答这个问题：" + coreQuestion
                    + "。直接回答，不要输出无关的图片描述。";
        }

        if (lastQuestion != null && !lastQuestion.isEmpty()
                && !lastQuestion.startsWith("[")
                && lastQuestion.length() < 50) {
            return "请仔细观察图片，只回答这个问题：" + lastQuestion
                    + "。直接回答，不要输出无关的图片描述。";
        }

        return "请仔细重新观察这张图片的细节，给出更准确的描述。";
    }

    @PreDestroy
    public void cleanup() {
        if (client != null) {
            client.close();
            log.info("iLink 客户端已关闭");
        }
    }
}
