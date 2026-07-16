package com.Myself.demo.bot;

import com.Myself.demo.command.Command;
import com.Myself.demo.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class CommandRouter {

    private final Map<String, Command> commands;

    public CommandRouter(List<Command> commandList) {
        this.commands = commandList.stream()
                .collect(Collectors.toMap(Command::getName, Function.identity()));
    }

    public String route(String input) {
        if (input == null || input.trim().isEmpty()) {
            return "请输入命令";
        }

        String[] parts = input.trim().split("\\s+", 2);
        String cmdName = parts[0].toLowerCase();
        String[] args = parts.length > 1 ? new String[]{parts[1]} : new String[0];

        Command cmd = commands.get(cmdName);
        if (cmd == null) {
            return "未知命令: " + cmdName + "，回复 help 查看帮助";
        }

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
