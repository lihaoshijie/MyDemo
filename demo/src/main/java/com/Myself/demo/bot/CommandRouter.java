package com.Myself.demo.bot;

import com.Myself.demo.command.Command;
import com.Myself.demo.exception.BusinessException;
import com.Myself.demo.service.ChatService;
import com.Myself.demo.service.LlmService;
import com.Myself.demo.service.LlmService.LlmResult;
import com.alibaba.dashscope.tools.FunctionDefinition;
import com.alibaba.dashscope.tools.ToolFunction;
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
    private final List<ToolFunction> tools;

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
            com.fasterxml.jackson.databind.JsonNode argsNode = objectMapper.readTree(fnArgs);
            String city = argsNode.has("city") ? argsNode.get("city").asText() : "";
            int days = argsNode.has("days") ? argsNode.get("days").asInt(1) : 1;
            if (!city.isEmpty()) {
                return new String[] { city, String.valueOf(days) };
            }
            return new String[0];
        } catch (Exception e) {
            log.warn("解析函数参数失败: {}", fnArgs, e);
            return new String[0];
        }
    }

    private List<ToolFunction> buildTools() {
        List<ToolFunction> tools = new ArrayList<>();

        tools.add(ToolFunction.builder()
                .function(FunctionDefinition.builder()
                        .name("weather")
                        .description("查询全球任意城市的实时天气或未来天气预报。支持所有城市，包括国内和国际城市（如北京、纽约、东京、伦敦等）。只支持实时天气和未来几天的预报，不支持查询历史天气（昨天、前天等）。对于主观天气感受（如热不热、冷不冷）或历史天气，请直接回答，不要调用此工具。")
                        .parameters(LlmService.buildWeatherParams())
                        .build())
                .build());

        tools.add(ToolFunction.builder()
                .function(FunctionDefinition.builder()
                        .name("help")
                        .description("显示可用命令列表和帮助信息")
                        .build())
                .build());

        tools.add(ToolFunction.builder()
                .function(FunctionDefinition.builder()
                        .name("status")
                        .description("查看系统运行状态，包括运行时间、内存使用、CPU核数等")
                        .build())
                .build());

        tools.add(ToolFunction.builder()
                .function(FunctionDefinition.builder()
                        .name("version")
                        .description("查看项目版本号、Spring Boot版本、Java版本等技术信息")
                        .build())
                .build());

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
