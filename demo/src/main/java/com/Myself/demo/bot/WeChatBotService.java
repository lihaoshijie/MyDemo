package com.Myself.demo.bot;

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

        client = ILinkClient.builder()
                .config(config)
                .onLogin(new OnLoginListener() {
                    @Override
                    public void onLoginSuccess(LoginContext context) {
                        log.info("iLink 登录成功, botId = {}", context.getBotId());
                        System.out.println("✓ 微信 Bot 登录成功!");
                    }

                    @Override
                    public void onLoginFailure(Throwable throwable) {
                        log.error("iLink 登录失败", throwable);
                        System.out.println("✗ 微信 Bot 登录失败: " + throwable.getMessage());
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
        String text = extractText(msg);
        if (text == null || text.isEmpty()) {
            log.info("收到非文本消息, fromUserId={}", fromUserId);
            return;
        }

        log.info("收到微信消息 from={}, text={}", fromUserId, text);

        String result = commandRouter.route(text);
        try {
            client.sendText(fromUserId, result);
            log.info("回复消息成功: {}", result);
        } catch (Exception e) {
            log.error("回复消息失败", e);
        }
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
