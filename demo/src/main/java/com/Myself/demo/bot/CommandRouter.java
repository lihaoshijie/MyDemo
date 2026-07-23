package com.Myself.demo.bot;

import com.Myself.demo.command.Command;
import com.Myself.demo.exception.BusinessException;
import com.Myself.demo.service.ChatService;
import com.Myself.demo.service.LlmService;
import com.Myself.demo.service.LlmService.LlmResult;
import com.alibaba.dashscope.tools.ToolFunction;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonArray;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class CommandRouter {

    private static final int MAX_TOOL_ROUNDS = 5;

    private final Map<String, Command> commands;
    private final ChatService chatService;
    private final LlmService llmService;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;

    public CommandRouter(List<Command> commandList, ChatService chatService,
                         LlmService llmService, @Lazy ToolRegistry toolRegistry) {
        this.commands = commandList.stream()
                .collect(Collectors.toMap(Command::getName, Function.identity()));
        this.chatService = chatService;
        this.llmService = llmService;
        this.toolRegistry = toolRegistry;
        this.objectMapper = new ObjectMapper();
    }

    public boolean hasCommand(String name) {
        return commands.containsKey(name.toLowerCase());
    }

    public String route(String input, String userId) {
        if (input == null || input.trim().isEmpty()) {
            return "请输入命令";
        }

        String trimmed = input.trim();
        String[] parts = trimmed.split("\\s+", 2);
        String cmdName = parts[0].toLowerCase();

        Command cmd = commands.get(cmdName);
        if (cmd != null) {
            String[] args = parts.length > 1 ? new String[]{parts[1]} : new String[0];
            return executeCommand(cmd, cmdName, args);
        }

        log.info("未匹配直接命令，LLM function calling: {}", trimmed);
        List<Map<String, String>> history = chatService.getHistory(userId);
        List<ToolFunction> tools = toolRegistry.buildTools();

        JsonArray messages = llmService.buildBaseMessages(userId, history);
        com.google.gson.JsonObject userMsg = new com.google.gson.JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", trimmed);
        messages.add(userMsg);

        LlmResult result = llmService.chatRaw(messages, tools);
        Set<String> calledKeys = new HashSet<>();
        int round = 0;

        while (result.isFunctionCall()) {
            List<LlmResult.FunctionCall> calls = result.getFunctionCalls();
            log.info("第{}轮工具调用: {}个", round + 1, calls.size());

            List<String> toolResults = new ArrayList<>();
            List<LlmResult.FunctionCall> executedCalls = new ArrayList<>();

            for (LlmResult.FunctionCall call : calls) {
                String key = call.name() + "|" + call.args();
                if (!calledKeys.add(key)) {
                    log.warn("重复调用拦截: {}", key);
                    toolResults.add("[已拦截：该工具和参数已被调用过]");
                    executedCalls.add(call);
                    continue;
                }

                String toolResult = executeCall(call, userId);
                toolResults.add(toolResult != null ? toolResult : "工具执行失败");
                executedCalls.add(call);
            }

            if (toolResults.isEmpty()) break;

            messages.add(llmService.buildAssistantWithToolCalls(executedCalls));
            for (int i = 0; i < executedCalls.size(); i++) {
                messages.add(llmService.buildToolResult(i, executedCalls.get(i), toolResults.get(i)));
            }

            round++;
            if (round >= MAX_TOOL_ROUNDS) {
                log.warn("达到最大轮次上限({})", MAX_TOOL_ROUNDS);
                // 最后一次 LLM 调用不带工具，让它基于已有信息作答
                result = llmService.chatRaw(messages, null);
                break;
            }

            result = llmService.chatRaw(messages, tools);
        }

        if (result.isText()) {
            String reply = result.getContent();
            chatService.addHistory(userId, trimmed, reply);
            return reply;
        }

        return "无法处理此消息";
    }

    private String executeCall(LlmResult.FunctionCall call, String userId) {
        String fnName = call.name();
        String fnArgs = call.args();

        Command fnCmd = commands.get(fnName);
        if (fnCmd != null) {
            try {
                String[] cmdArgs = extractArgs(fnArgs);
                return executeCommand(fnCmd, fnName, cmdArgs);
            } catch (Exception e) {
                log.warn("命令执行失败: {}", fnName, e);
                return "命令执行失败: " + e.getMessage();
            }
        }

        return toolRegistry.execute(fnName, fnArgs, userId);
    }

    private String[] extractArgs(String fnArgs) {
        try {
            if (fnArgs == null || fnArgs.trim().isEmpty()) {
                return new String[0];
            }
            JsonNode argsNode = objectMapper.readTree(fnArgs);
            String city = argsNode.has("city") ? argsNode.get("city").asText() : "";
            int days = argsNode.has("days") ? argsNode.get("days").asInt(1) : 1;
            if (!city.isEmpty()) {
                return new String[]{city, String.valueOf(days)};
            }
            return new String[0];
        } catch (Exception e) {
            log.warn("解析函数参数失败: {}", fnArgs, e);
            return new String[0];
        }
    }

    private String executeCommand(Command cmd, String cmdName, String[] args) {
        try {
            return cmd.execute(args);
        } catch (BusinessException e) {
            log.warn("业务异常: {}", e.getMessage());
            return "[错误] " + e.getMessage();
        } catch (Exception e) {
            log.error("命令执行异常: {}", cmdName, e);
            return "[错误] 系统异常";
        }
    }
}
