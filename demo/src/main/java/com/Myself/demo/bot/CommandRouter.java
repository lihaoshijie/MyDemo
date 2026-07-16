package com.Myself.demo.bot;

import com.Myself.demo.command.Command;
import com.Myself.demo.exception.BusinessException;
import com.Myself.demo.service.ChatService;
import com.Myself.demo.service.LlmService;
import com.Myself.demo.service.LlmService.LlmResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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
    private final ObjectMapper objectMapper;
    private final List<Map<String, Object>> tools;

    public CommandRouter(List<Command> commandList, ChatService chatService, LlmService llmService) {
        this.commands = commandList.stream()
                .collect(Collectors.toMap(Command::getName, Function.identity()));
        this.chatService = chatService;
        this.llmService = llmService;
        this.objectMapper = new ObjectMapper();
        this.tools = buildTools();
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
            String[] args = parts.length > 1 ? new String[] { parts[1] } : new String[0];
            return executeCommand(cmd, cmdName, args);
        }

        log.info("未匹配直接命令，LLM function calling: {}", trimmed);
        List<Map<String, String>> history = chatService.getHistory(userId);
        LlmResult result = llmService.chat(trimmed, tools, history);

        if (result.isFunctionCall()) {
            String fnName = result.getFunctionName();
            String fnArgs = result.getFunctionArgs();
            Command fnCmd = commands.get(fnName);

            if (fnCmd != null) {
                log.info("Function calling: {}({})", fnName, fnArgs);
                String[] cmdArgs = extractArgs(fnArgs);
                String cmdResult = executeCommand(fnCmd, fnName, cmdArgs);
                chatService.addHistory(userId, trimmed, cmdResult);
                return cmdResult;
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
            if (argsNode.has("city")) {
                return new String[] { argsNode.get("city").asText() };
            }
            return new String[0];
        } catch (Exception e) {
            log.warn("解析函数参数失败: {}", fnArgs, e);
            String clean = fnArgs.replaceAll("[{}\"\\s]", "");
            if (clean.contains(":")) {
                return new String[] { clean.split(":")[1] };
            }
            return new String[] { clean };
        }
    }

    private List<Map<String, Object>> buildTools() {
        List<Map<String, Object>> tools = new ArrayList<>();

        tools.add(Map.of(
            "type", "function",
            "function", Map.of(
                "name", "weather",
                "description", "查询某个城市的实时天气，包括温度、湿度、风向等",
                "parameters", Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "city", Map.of(
                            "type", "string",
                            "description", "城市名称，例如：北京、上海、杭州、广州"
                        )
                    ),
                    "required", List.of("city")
                )
            )
        ));

        tools.add(Map.of(
            "type", "function",
            "function", Map.of(
                "name", "help",
                "description", "显示可用命令列表和帮助信息"
            )
        ));

        tools.add(Map.of(
            "type", "function",
            "function", Map.of(
                "name", "status",
                "description", "查看系统运行状态，包括运行时间、内存使用、CPU核数等"
            )
        ));

        tools.add(Map.of(
            "type", "function",
            "function", Map.of(
                "name", "version",
                "description", "查看项目版本号、Spring Boot版本、Java版本等技术信息"
            )
        ));

        return tools;
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
