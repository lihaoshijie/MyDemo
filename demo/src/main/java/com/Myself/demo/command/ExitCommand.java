package com.Myself.demo.command;

import org.springframework.stereotype.Component;

@Component
public class ExitCommand implements Command {

    @Override
    public String getName() {
        return "exit";
    }

    @Override
    public String execute(String[] args) {
        return "该命令仅限 CLI 使用";
    }
}
