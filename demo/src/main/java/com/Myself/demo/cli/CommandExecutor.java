package com.Myself.demo.cli;

import com.Myself.demo.command.Command;
import com.Myself.demo.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class CommandExecutor {

    private final Map<String, Command> commands;
    private final Scanner scanner = new Scanner(System.in);

    @Autowired
    public CommandExecutor(List<Command> commandList) {
        this.commands = commandList.stream()
                .collect(Collectors.toMap(Command::getName, Function.identity()));
        log.info("已注册 {} 个命令: {}", commands.size(), commands.keySet());
    }

    public void start() {
        System.out.println("=== SpringBoot Demo ===");
        System.out.println("输入 help 查看可用命令");
        System.out.println("=======================");

        while (true) {
            System.out.print("> ");
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) continue;

            String[] parts = input.split("\\s+");
            String cmdName = parts[0].toLowerCase();
            String[] args = java.util.Arrays.copyOfRange(parts, 1, parts.length);

            if (cmdName.equals("exit")) {
                System.out.println("再见！");
                break;
            }

            executeCommand(cmdName, args);
        }
    }

    private void executeCommand(String cmdName, String[] args) {
        Command command = commands.get(cmdName);
        if (command == null) {
            System.out.println("[错误] 未知命令: " + cmdName + "，输入 help 查看帮助");
            return;
        }

        try {
            String result = command.execute(args);
            System.out.println(result);
        } catch (BusinessException e) {
            log.warn("业务异常: {}", e.getMessage());
            System.out.println("[错误] " + e.getMessage());
        } catch (Exception e) {
            log.error("执行命令异常: {}", cmdName, e);
            System.out.println("[错误] 系统异常，请联系管理员");
        }
    }
}
