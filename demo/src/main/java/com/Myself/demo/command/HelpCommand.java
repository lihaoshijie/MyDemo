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
        StringBuilder sb = new StringBuilder();
        sb.append("========== 帮助信息 ==========\n");
        sb.append("可用命令:\n");
        sb.append("  help              - 显示帮助信息\n");
        sb.append("  version           - 显示版本信息\n");
        sb.append("  status            - 显示程序状态\n");
        sb.append("  weather <城市名>  - 查询指定城市天气\n");
        sb.append("  exit              - 退出程序\n");
        sb.append("==============================");
        return sb.toString();
    }
}
