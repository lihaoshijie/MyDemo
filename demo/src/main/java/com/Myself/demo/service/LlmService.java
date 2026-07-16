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
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class LlmService {

    private final WebClient webClient;
    private final String apiKey;
    private final String model;
    private final String systemPrompt;
    private final ObjectMapper objectMapper;

    public LlmService(
            @Value("${llm.api-key}") String apiKey,
            @Value("${llm.model}") String model,
            @Value("${llm.base-url}") String baseUrl,
            @Value("${llm.system-prompt}") String systemPrompt) {
        this.apiKey = apiKey;
        this.model = model;
        this.systemPrompt = systemPrompt;
        this.objectMapper = new ObjectMapper();
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
        log.info("LlmService 初始化完成, model: {}", model);
    }

    public String chat(List<Map<String, String>> messages) {
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", model);

            ArrayNode messagesNode = requestBody.putArray("messages");

            ObjectNode systemMsg = objectMapper.createObjectNode();
            systemMsg.put("role", "system");
            systemMsg.put("content", systemPrompt);
            messagesNode.add(systemMsg);

            for (Map<String, String> msg : messages) {
                ObjectNode msgNode = objectMapper.createObjectNode();
                msgNode.put("role", msg.get("role"));
                msgNode.put("content", msg.get("content"));
                messagesNode.add(msgNode);
            }

            String requestJson = objectMapper.writeValueAsString(requestBody);
            log.debug("LLM 请求: {}", requestJson);

            String response = webClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestJson)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.debug("LLM 响应: {}", response);

            return extractContent(response);

        } catch (WebClientResponseException e) {
            log.error("LLM API HTTP错误, 状态码: {}", e.getStatusCode(), e);
            return "AI 服务暂时不可用，请稍后再试";
        } catch (Exception e) {
            log.error("LLM 调用异常", e);
            return "AI 服务调用失败: " + e.getMessage();
        }
    }

    private String extractContent(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                JsonNode firstChoice = choices.get(0);
                JsonNode message = firstChoice.path("message");
                return message.path("content").asText("无回复内容");
            }
            return "无回复内容";
        } catch (Exception e) {
            log.error("解析 LLM 响应失败", e);
            return "解析 AI 回复失败";
        }
    }
}
