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

    public LlmResult chat(String userMessage, List<Map<String, Object>> tools) {
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", model);

            ArrayNode messagesNode = requestBody.putArray("messages");

            ObjectNode systemMsg = objectMapper.createObjectNode();
            systemMsg.put("role", "system");
            systemMsg.put("content", systemPrompt);
            messagesNode.add(systemMsg);

            ObjectNode userMsg = objectMapper.createObjectNode();
            userMsg.put("role", "user");
            userMsg.put("content", userMessage);
            messagesNode.add(userMsg);

            if (tools != null && !tools.isEmpty()) {
                ArrayNode toolsNode = objectMapper.valueToTree(tools);
                requestBody.set("tools", toolsNode);
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
            return parseResponse(response);

        } catch (Exception e) {
            log.error("LLM 调用异常", e);
            return LlmResult.text("AI 服务暂时不可用，请稍后再试");
        }
    }

    public LlmResult chat(String userMessage, List<Map<String, Object>> tools, List<Map<String, String>> history) {
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", model);

            ArrayNode messagesNode = requestBody.putArray("messages");

            ObjectNode systemMsg = objectMapper.createObjectNode();
            systemMsg.put("role", "system");
            systemMsg.put("content", systemPrompt);
            messagesNode.add(systemMsg);

            if (history != null) {
                for (Map<String, String> msg : history) {
                    ObjectNode msgNode = objectMapper.createObjectNode();
                    msgNode.put("role", msg.get("role"));
                    msgNode.put("content", msg.get("content"));
                    messagesNode.add(msgNode);
                }
            }

            ObjectNode userMsg = objectMapper.createObjectNode();
            userMsg.put("role", "user");
            userMsg.put("content", userMessage);
            messagesNode.add(userMsg);

            if (tools != null && !tools.isEmpty()) {
                ArrayNode toolsNode = objectMapper.valueToTree(tools);
                requestBody.set("tools", toolsNode);
            }

            String requestJson = objectMapper.writeValueAsString(requestBody);
            log.debug("LLM 请求(with tools+history): {}", requestJson);

            String response = webClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestJson)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.debug("LLM 响应: {}", response);
            return parseResponse(response);

        } catch (Exception e) {
            log.error("LLM 调用异常", e);
            return LlmResult.text("AI 服务暂时不可用，请稍后再试");
        }
    }

    private LlmResult parseResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                return LlmResult.text("无回复内容");
            }

            JsonNode message = choices.get(0).path("message");
            String finishReason = choices.get(0).path("finish_reason").asText("");

            if ("tool_calls".equals(finishReason)) {
                JsonNode toolCalls = message.path("tool_calls");
                if (toolCalls.isArray() && !toolCalls.isEmpty()) {
                    JsonNode firstCall = toolCalls.get(0);
                    String functionName = firstCall.path("function").path("name").asText();
                    String functionArgs = firstCall.path("function").path("arguments").asText();
                    log.info("LLM 选择调函数: {}({})", functionName, functionArgs);
                    return LlmResult.functionCall(functionName, functionArgs);
                }
            }

            return LlmResult.text(message.path("content").asText("无回复内容"));

        } catch (Exception e) {
            log.error("解析 LLM 响应失败", e);
            return LlmResult.text("无回复内容");
        }
    }

    public static class LlmResult {
        private final String type; // "text" or "function_call"
        private final String content;
        private final String functionName;
        private final String functionArgs;

        private LlmResult(String type, String content, String functionName, String functionArgs) {
            this.type = type;
            this.content = content;
            this.functionName = functionName;
            this.functionArgs = functionArgs;
        }

        public static LlmResult text(String content) {
            return new LlmResult("text", content, null, null);
        }

        public static LlmResult functionCall(String name, String args) {
            return new LlmResult("function_call", null, name, args);
        }

        public boolean isText() { return "text".equals(type); }
        public boolean isFunctionCall() { return "function_call".equals(type); }
        public String getContent() { return content; }
        public String getFunctionName() { return functionName; }
        public String getFunctionArgs() { return functionArgs; }
    }
}
