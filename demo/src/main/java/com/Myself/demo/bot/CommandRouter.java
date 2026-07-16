package com.Myself.demo.bot;

import com.Myself.demo.command.Command;
import com.Myself.demo.exception.BusinessException;
import com.Myself.demo.service.ChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Component
public class CommandRouter {

    private final Map<String, Command> commands;
    private final ChatService chatService;

    private static final Pattern WEATHER_PATTERN = Pattern.compile(
            "^(?:查?|问?|看看?|告诉我)?\\s*(.+?)\\s*(?:的)?\\s*天气(?:怎么样|如何|啥样|怎样)?\\s*$"
    );
    private static final Pattern WEATHER_PATTERN2 = Pattern.compile(
            "^天气\\s+(.+?)\\s*$"
    );

    public CommandRouter(List<Command> commandList, ChatService chatService) {
        this.commands = commandList.stream()
                .collect(Collectors.toMap(Command::getName, Function.identity()));
        this.chatService = chatService;
    }

    public String route(String input, String userId) {
        if (input == null || input.trim().isEmpty()) {
            return "请输入命令";
        }

        String trimmed = input.trim();
        String[] parts = trimmed.split("\\s+", 2);
        String cmdName = parts[0].toLowerCase();
        String[] args = parts.length > 1 ? new String[] { parts[1] } : new String[0];

        Command cmd = commands.get(cmdName);
        if (cmd != null) {
            return executeCommand(cmd, cmdName, args);
        }

        String weatherCity = extractWeatherCity(trimmed);
        if (weatherCity != null && !weatherCity.isEmpty()) {
            log.info("识别到天气查询意图: {}", weatherCity);
            Command weatherCmd = commands.get("weather");
            if (weatherCmd != null) {
                return executeCommand(weatherCmd, "weather", new String[] { weatherCity });
            }
        }

        return chatService.chat(userId, input);
    }

    private String extractWeatherCity(String input) {
        Matcher m1 = WEATHER_PATTERN.matcher(input);
        if (m1.matches()) {
            return m1.group(1).trim();
        }
        Matcher m2 = WEATHER_PATTERN2.matcher(input);
        if (m2.matches()) {
            return m2.group(1).trim();
        }
        return null;
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
