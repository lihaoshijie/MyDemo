package com.Myself.demo.bot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.wechat.ilink.sdk.core.login.LoginContext;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;

@Slf4j
public class SessionManager {

    private static final File SESSION_FILE = new File("ilink-session.json");
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void save(LoginContext ctx) {
        try {
            SessionData data = new SessionData(
                    ctx.getBotToken(), ctx.getUserId(), ctx.getBotId(), ctx.getBaseUrl());
            objectMapper.writeValue(SESSION_FILE, data);
            log.info("登录会话已保存: {}", SESSION_FILE.getAbsolutePath());
        } catch (IOException e) {
            log.warn("保存登录会话失败: {}", e.getMessage());
        }
    }

    public static LoginContext load() {
        if (!SESSION_FILE.exists()) {
            log.info("未找到历史登录会话");
            return null;
        }
        try {
            SessionData data = objectMapper.readValue(SESSION_FILE, SessionData.class);
            LoginContext ctx = new LoginContext(data.botToken, data.userId, data.botId, data.baseUrl);
            log.info("加载历史登录会话成功, botId={}", data.botId);
            return ctx;
        } catch (Exception e) {
            log.warn("加载登录会话失败: {}", e.getMessage());
            SESSION_FILE.delete();
            return null;
        }
    }

    public static void clear() {
        if (SESSION_FILE.exists()) {
            SESSION_FILE.delete();
            log.info("登录会话已清除");
        }
    }

    private static class SessionData {
        public String botToken;
        public String userId;
        public String botId;
        public String baseUrl;

        public SessionData() {}

        public SessionData(String botToken, String userId, String botId, String baseUrl) {
            this.botToken = botToken;
            this.userId = userId;
            this.botId = botId;
            this.baseUrl = baseUrl;
        }
    }
}
