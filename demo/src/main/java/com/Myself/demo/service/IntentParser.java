package com.Myself.demo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Service
public class IntentParser {

    private final WebClient webClient;
    private final String model;
    private final ObjectMapper objectMapper;

    private static final String INTENT_PROMPT = """
            分析用户消息，判断是否匹配以下命令。返回 JSON：
            {"command":"命令名","args":"参数","match":true/false}

            命令列表：
            - weather <城市名>：用户想查某个城市的天气。从消息中提取城市名作为 args。
            - status：用户想看系统状态/运行状态/内存使用等。
            - version：用户想看版本信息/用了什么技术栈。
            - help：用户想看帮助/不知道能干嘛/问有哪些功能。

            规则：
            1. 如果用户只是在聊天/问好/问问题/讲故事/讲笑话，返回 {"command":"chat","args":"","match":false}
            2. 如果用户明确想执行某个命令（哪怕用的是口语），返回 match=true 并提取参数
            3. 只返回 JSON，不要任何其他内容

            用户消息：%s""";

    public IntentParser(
            @Value("${llm.api-key}") String apiKey,
            @Value("${llm.model}") String model,
            @Value("${llm.base-url}") String baseUrl) {
        this.model = model;
        this.objectMapper = new ObjectMapper();
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
        log.info("IntentParser 初始化完成");
    }

    public ParsedIntent parse(String userMessage) {
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", model);
            requestBody.put("temperature", 0.1);

            ArrayNode messagesNode = requestBody.putArray("messages");
            ObjectNode msgNode = objectMapper.createObjectNode();
            msgNode.put("role", "user");
            msgNode.put("content", INTENT_PROMPT.formatted(userMessage));
            messagesNode.add(msgNode);

            String response = webClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(objectMapper.writeValueAsString(requestBody))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return extractIntent(response);

        } catch (Exception e) {
            log.error("意图解析失败: {}", e.getMessage());
            return new ParsedIntent("chat", "", false);
        }
    }

    private ParsedIntent extractIntent(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            String content = root.path("choices").get(0).path("message").path("content").asText();

            content = content.trim();
            if (content.startsWith("```")) {
                content = content.replaceAll("```json\\s*", "").replaceAll("```", "").trim();
            }

            JsonNode node = objectMapper.readTree(content);
            String command = node.path("command").asText("chat");
            String args = node.path("args").asText("");
            boolean match = node.path("match").asBoolean(false);

            log.info("意图解析: command={}, args={}, match={}", command, args, match);
            return new ParsedIntent(command, args, match);

        } catch (Exception e) {
            log.error("解析意图 JSON 失败: {}", e.getMessage());
            return new ParsedIntent("chat", "", false);
        }
    }

    public static class ParsedIntent {
        private final String command;
        private final String args;
        private final boolean match;

        public ParsedIntent(String command, String args, boolean match) {
            this.command = command;
            this.args = args;
            this.match = match;
        }

        public String getCommand() { return command; }
        public String getArgs() { return args; }
        public boolean isMatch() { return match; }
    }
}
