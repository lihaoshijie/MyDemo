package com.Myself.demo.command;

import org.springframework.stereotype.Component;

@Component
public class HelpCommand implements Command {

    @Override
    public String getName() {
        return "help";
    }

    @Override
    public String execute(String[] args) {
        return "【MyDemo 帮助】\n\n" +
               "可用命令:\n\n" +
               "help\n" +
               "显示帮助信息\n\n" +
               "version\n" +
               "显示版本信息\n\n" +
               "status\n" +
               "显示程序状态\n\n" +
               "weather 城市名\n" +
               "查询指定城市天气\n\n" +
               "exit\n" +
               "退出程序\n\n" +
               "示例: weather 杭州";
    }
}
