package com.Myself.demo.service;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationOutput.Choice;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.alibaba.dashscope.tools.ToolBase;
import com.alibaba.dashscope.tools.ToolCallBase;
import com.alibaba.dashscope.tools.ToolCallFunction;
import com.alibaba.dashscope.tools.ToolFunction;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class LlmService {

    private final String apiKey;
    private final String model;
    private final String systemPrompt;
    private final Generation generation;

    public LlmService(
            @Value("${llm.api-key}") String apiKey,
            @Value("${llm.model}") String model,
            @Value("${llm.system-prompt}") String systemPrompt) {
        this.apiKey = apiKey;
        this.model = model;
        this.systemPrompt = systemPrompt;
        this.generation = new Generation();
        log.info("LlmService 初始化完成, model: {}", model);
    }

    public LlmResult chat(String userMessage, List<? extends ToolBase> tools, List<Map<String, String>> history) {
        try {
            List<Message> messages = new ArrayList<>();
            messages.add(Message.builder()
                    .role(Role.SYSTEM.getValue())
                    .content(systemPrompt)
                    .build());
            if (history != null) {
                for (Map<String, String> msg : history) {
                    messages.add(Message.builder()
                            .role(msg.get("role"))
                            .content(msg.get("content"))
                            .build());
                }
            }
            messages.add(Message.builder()
                    .role(Role.USER.getValue())
                    .content(userMessage)
                    .build());

            GenerationParam.GenerationParamBuilder<?, ?> builder = GenerationParam.builder()
                    .apiKey(apiKey)
                    .model(model)
                    .messages(messages)
                    .resultFormat("message");

            if (tools != null && !tools.isEmpty()) {
                builder.tools(new ArrayList<>(tools));
            }

            GenerationResult result = generation.call(builder.build());
            return parseResult(result);

        } catch (ApiException | NoApiKeyException | InputRequiredException e) {
            log.error("LLM 调用异常", e);
            return LlmResult.text("AI 服务暂时不可用，请稍后再试");
        }
    }

    private LlmResult parseResult(GenerationResult result) {
        List<Choice> choices = result.getOutput().getChoices();
        if (choices == null || choices.isEmpty()) {
            return LlmResult.text("无回复内容");
        }

        Choice choice = choices.get(0);
        Message msg = choice.getMessage();

        if (msg.getToolCalls() != null && !msg.getToolCalls().isEmpty()) {
            ToolCallBase toolCall = msg.getToolCalls().get(0);
            if ("function".equals(toolCall.getType())) {
                ToolCallFunction funcCall = (ToolCallFunction) toolCall;
                String fnName = funcCall.getFunction().getName();
                String fnArgs = funcCall.getFunction().getArguments();
                log.info("Function calling: {}({})", fnName, fnArgs);
                return LlmResult.functionCall(fnName, fnArgs);
            }
        }

        return LlmResult.text(msg.getContent());
    }

    public static JsonObject buildWeatherParams() {
        String schema = """
                {"type":"object","properties":{"city":{"type":"string","description":"城市名称，如：北京、上海、杭州"},"days":{"type":"integer","description":"查询未来几天天气。1=今天实时天气，3=未来三天，7=未来七天，15=未来十五天。默认为1","enum":[1,3,7,15]}},"required":["city"]}""";
        return JsonParser.parseString(schema).getAsJsonObject();
    }

    public static class LlmResult {
        private final String type;
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
