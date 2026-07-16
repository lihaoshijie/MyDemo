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

import java.util.Map;

@Slf4j
@Service
public class IntentParser {

    private final WebClient webClient;
    private final String apiKey;
    private final String model;
    private final ObjectMapper objectMapper;

    public IntentParser(
            @Value("${llm.api-key}") String apiKey,
            @Value("${llm.model}") String model,
            @Value("${llm.base-url}") String baseUrl) {
        this.apiKey = apiKey;
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
        String prompt = """ 
                请分析用户意图，返回 JSON 格式：
                {"command": "命令名", "args": "参数", "confidence": 0.0-1.0}

                可用命令：
                - weather: 查询天气，参数是城市名
                - help: 查看帮助
                - version: 查看版本
                - status: 查看状态

                用户消息: %s

                如果无法识别意图，返回：
                {"command": "chat", "args": "", "confidence": 0.0

                只返回 JSON，不要其他内容。""".formatted(userMessage);

        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", model);
            requestBody.put("temperature", 0.1);

            ArrayNode messagesNode = requestBody.putArray("messages");
            ObjectNode msgNode = objectMapper.createObjectNode();
            msgNode.put("role", "user");
            msgNode.put("content", prompt);
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
            log.error("意图解析失败", e);
            return new ParsedIntent("chat", "", 0.0);
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

            JsonNode intentNode = objectMapper.readTree(content);
            String command = intentNode.path("command").asText("chat");
            String args = intentNode.path("args").asText("");
            double confidence = intentNode.path("confidence").asDouble(0.0);

            log.info("意图解析结果: command={}, args={}, confidence={}", command, args, confidence);
            return new ParsedIntent(command, args, confidence);

        } catch (Exception e) {
            log.error("解析意图 JSON 失败", e);
            return new ParsedIntent("chat", "", 0.0);
        }
    }

    public static class ParsedIntent {
        private final String command;
        private final String args;
        private final double confidence;

        public ParsedIntent(String command, String args, double confidence) {
            this.command = command;
            this.args = args;
            this.confidence = confidence;
        }

        public String getCommand() { return command; }
        public String getArgs() { return args; }
        public double getConfidence() { return confidence; }
    }
}
