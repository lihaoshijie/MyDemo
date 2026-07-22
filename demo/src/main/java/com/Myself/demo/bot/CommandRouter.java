package com.Myself.demo.bot;

import com.Myself.demo.command.Command;
import com.Myself.demo.exception.BusinessException;
import com.Myself.demo.service.ChatService;
import com.Myself.demo.service.LlmService;
import com.Myself.demo.service.LlmService.LlmResult;
import com.alibaba.dashscope.tools.ToolFunction;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class CommandRouter {

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
        LlmResult result = llmService.chat(trimmed, tools, history, userId);

        if (result.isFunctionCall()) {
            String fnName = result.getFunctionName();
            String fnArgs = result.getFunctionArgs();
            String toolResult;

            toolResult = toolRegistry.execute(fnName, fnArgs, userId);
            if (toolResult == null) {
                Command fnCmd = commands.get(fnName);
                if (fnCmd != null) {
                    String[] cmdArgs = extractArgs(fnArgs);
                    toolResult = executeCommand(fnCmd, fnName, cmdArgs);
                }
            }

            if (toolResult != null) {
                log.info("工具执行: {} → {}", fnName, toolResult.substring(0, Math.min(50, toolResult.length())));
                LlmResult reactResult = llmService.continueWithToolResult(
                        trimmed, history, fnName, fnArgs, toolResult, tools, userId);
                if (reactResult.isText()) {
                    String reply = reactResult.getContent();
                    chatService.addHistory(userId, trimmed, reply);
                    return reply;
                }
                return toolResult;
            }
        }

        if (result.isText()) {
            String reply = result.getContent();
            chatService.addHistory(userId, trimmed, reply);
            return reply;
        }

        return "无法处理此消息";
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
