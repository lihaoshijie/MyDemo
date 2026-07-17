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

@Slf4j
@Service
public class WeChatBotService {

    @Autowired
    private CommandRouter commandRouter;

    @Autowired
    private ImageService imageService;

    private ILinkClient client;

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
        String result = commandRouter.route(text, fromUserId);
        sendReply(fromUserId, result);
    }

    private void handleImageMessage(String fromUserId, MessageItem imageItem, String text) {
        String question = text != null && !text.isEmpty() ? text : "请描述这张图片的内容";
        log.info("收到图片消息 from={}, question={}", fromUserId, question);

        try {
            byte[] imageBytes = client.downloadImageFromMessageItem(imageItem);
            String answer = imageService.recognizeImage(imageBytes, question);
            sendReply(fromUserId, answer);
        } catch (Exception e) {
            log.error("处理图片消息失败", e);
            try {
                client.sendText(fromUserId, "抱歉，图片识别失败，请稍后再试");
            } catch (Exception ex) {
                log.error("发送错误回复失败", ex);
            }
        }
    }

    private void sendReply(String fromUserId, String result) {
        if (result == null) return;

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

    @PreDestroy
    public void cleanup() {
        if (client != null) {
            client.close();
            log.info("iLink 客户端已关闭");
        }
    }
}
