package com.Myself.demo.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class LlmService {

    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final String systemPrompt;
    private final MemoryService memoryService;
    private final HttpClient httpClient;
    private final Gson gson = new Gson();

    public LlmService(
            @Value("${llm.api-key}") String apiKey,
            @Value("${llm.model}") String model,
            @Value("${llm.base-url}") String baseUrl,
            @Value("${llm.system-prompt}") String systemPrompt,
            MemoryService memoryService) {
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl;
        this.systemPrompt = systemPrompt;
        this.memoryService = memoryService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        log.info("LlmService 初始化完成, model: {}, baseUrl: {}", model, baseUrl);
    }

    private String buildSystemPrompt(String userId) {
        String facts = memoryService.getFormattedFacts(userId);
        return facts.isEmpty() ? systemPrompt : systemPrompt + "\n\n用户信息：" + facts + "。";
    }

    public String chat(String userId, List<Map<String, String>> history) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("model", model);
            body.addProperty("max_tokens", 2048);

            JsonArray messages = buildBaseMessages(userId, history);
            body.add("messages", messages);

            JsonObject result = callApi(body);
            return extractContent(result);
        } catch (Exception e) {
            log.error("LLM 对话调用异常", e);
            return "AI 服务暂时不可用，请稍后再试";
        }
    }

    public LlmResult chat(String userMessage, List<? extends com.alibaba.dashscope.tools.ToolBase> tools, List<Map<String, String>> history, String userId) {
        try {
            JsonArray messages = buildBaseMessages(userId, history);
            JsonObject userMsg = new JsonObject();
            userMsg.addProperty("role", "user");
            userMsg.addProperty("content", userMessage);
            messages.add(userMsg);

            return chatRaw(messages, tools);
        } catch (Exception e) {
            log.error("LLM 调用异常", e);
            return LlmResult.text("AI 服务暂时不可用，请稍后再试");
        }
    }

    public LlmResult chatRaw(JsonArray messages, List<? extends com.alibaba.dashscope.tools.ToolBase> tools) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("model", model);
            body.addProperty("max_tokens", 4096);
            body.add("messages", messages);
            addTools(body, tools);

            JsonObject result = callApi(body);
            return parseResult(result);
        } catch (Exception e) {
            log.error("LLM chatRaw 异常", e);
            return LlmResult.text("AI 服务暂时不可用，请稍后再试");
        }
    }

    public JsonArray buildBaseMessages(String userId, List<Map<String, String>> history) {
        JsonArray messages = new JsonArray();
        JsonObject sysMsg = new JsonObject();
        sysMsg.addProperty("role", "system");
        sysMsg.addProperty("content", buildSystemPrompt(userId));
        messages.add(sysMsg);

        if (history != null) {
            for (Map<String, String> msg : history) {
                JsonObject m = new JsonObject();
                m.addProperty("role", msg.get("role"));
                m.addProperty("content", msg.get("content"));
                messages.add(m);
            }
        }
        return messages;
    }

    public JsonObject buildAssistantWithToolCalls(List<LlmResult.FunctionCall> calls) {
        JsonObject msg = new JsonObject();
        msg.addProperty("role", "assistant");
        msg.add("content", null);
        JsonArray toolCalls = new JsonArray();
        for (int i = 0; i < calls.size(); i++) {
            LlmResult.FunctionCall call = calls.get(i);
            JsonObject tc = new JsonObject();
            tc.addProperty("id", "call_" + i + "_" + call.name());
            tc.addProperty("type", "function");
            JsonObject fn = new JsonObject();
            fn.addProperty("name", call.name());
            fn.addProperty("arguments", call.args());
            tc.add("function", fn);
            toolCalls.add(tc);
        }
        msg.add("tool_calls", toolCalls);
        return msg;
    }

    public JsonObject buildToolResult(int index, LlmResult.FunctionCall call, String result) {
        JsonObject msg = new JsonObject();
        msg.addProperty("role", "tool");
        msg.addProperty("content", result);
        msg.addProperty("tool_call_id", "call_" + index + "_" + call.name());
        msg.addProperty("name", call.name());
        return msg;
    }

    private void addTools(JsonObject body, List<? extends com.alibaba.dashscope.tools.ToolBase> tools) {
        if (tools == null || tools.isEmpty()) return;
        JsonArray toolsArray = new JsonArray();
        for (com.alibaba.dashscope.tools.ToolBase tool : tools) {
            if (tool instanceof com.alibaba.dashscope.tools.ToolFunction) {
                com.alibaba.dashscope.tools.FunctionDefinition fd = ((com.alibaba.dashscope.tools.ToolFunction) tool).getFunction();
                JsonObject toolObj = new JsonObject();
                toolObj.addProperty("type", "function");
                JsonObject funcObj = new JsonObject();
                funcObj.addProperty("name", fd.getName());
                funcObj.addProperty("description", fd.getDescription());
                if (fd.getParameters() != null) {
                    funcObj.add("parameters", gson.toJsonTree(fd.getParameters()));
                }
                toolObj.add("function", funcObj);
                toolsArray.add(toolObj);
            }
        }
        body.add("tools", toolsArray);
    }

    private JsonObject callApi(JsonObject body) throws Exception {
        String json = gson.toJson(body);
        log.debug("LLM API 请求: model={}", body.get("model").getAsString());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.error("LLM API 错误: status={}, body={}", response.statusCode(), response.body());
            throw new RuntimeException("API error: " + response.statusCode());
        }

        return gson.fromJson(response.body(), JsonObject.class);
    }

    private String extractContent(JsonObject result) {
        JsonArray choices = result.getAsJsonArray("choices");
        if (choices == null || choices.isEmpty()) return "无回复内容";
        JsonObject msg = choices.get(0).getAsJsonObject().getAsJsonObject("message");
        return msg != null && msg.has("content") ? msg.get("content").getAsString() : "无回复内容";
    }

    private LlmResult parseResult(JsonObject result) {
        JsonArray choices = result.getAsJsonArray("choices");
        if (choices == null || choices.isEmpty()) return LlmResult.text("无回复内容");

        JsonObject msg = choices.get(0).getAsJsonObject().getAsJsonObject("message");
        if (msg == null) return LlmResult.text("无回复内容");

        if (msg.has("tool_calls") && !msg.get("tool_calls").isJsonNull()) {
            JsonArray toolCalls = msg.getAsJsonArray("tool_calls");
            if (toolCalls != null && !toolCalls.isEmpty()) {
                List<LlmResult.FunctionCall> calls = new ArrayList<>();
                for (int i = 0; i < toolCalls.size(); i++) {
                    JsonObject tc = toolCalls.get(i).getAsJsonObject();
                    JsonObject fn = tc.getAsJsonObject("function");
                    String name = fn.get("name").getAsString();
                    String args = fn.get("arguments").getAsString();
                    calls.add(new LlmResult.FunctionCall(name, args));
                }
                log.info("Function calling: {} tool(s)", calls.size());
                return LlmResult.functionCall(calls);
            }
        }

        String content = msg.has("content") ? msg.get("content").getAsString() : "";
        return LlmResult.text(content);
    }

    public static class LlmResult {
        private final String type;
        private final String content;
        private final List<FunctionCall> functionCalls;

        private LlmResult(String type, String content, List<FunctionCall> functionCalls) {
            this.type = type;
            this.content = content;
            this.functionCalls = functionCalls;
        }

        public static LlmResult text(String content) {
            return new LlmResult("text", content, List.of());
        }

        public static LlmResult functionCall(List<FunctionCall> calls) {
            return new LlmResult("function_call", null, calls);
        }

        public boolean isText() { return "text".equals(type); }
        public boolean isFunctionCall() { return "function_call".equals(type); }
        public String getContent() { return content; }
        public List<FunctionCall> getFunctionCalls() { return functionCalls; }

        public String getFunctionName() { return functionCalls.isEmpty() ? null : functionCalls.get(0).name(); }
        public String getFunctionArgs() { return functionCalls.isEmpty() ? null : functionCalls.get(0).args(); }

        public record FunctionCall(String name, String args) {}
    }
}
