package com.Myself.demo.tool;

import com.Myself.demo.command.Command;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class HelpTool implements Command {

    private final List<Command> commands;

    public HelpTool(List<Command> commands) {
        this.commands = commands;
    }

    @Override
    public String getName() {
        return "help";
    }

    @Override
    public String execute(String[] args) {
        return showHelp();
    }

    @Tool(name = "help", value = "显示可用命令列表和帮助信息")
    public String showHelp() {
        StringBuilder sb = new StringBuilder("可用命令：\n");
        for (Command cmd : commands) {
            sb.append("/").append(cmd.getName()).append("\n");
        }
        return sb.toString();
    }

    @Tool(name = "status", value = "查看系统运行状态，包括运行时间、内存使用、CPU核数等")
    public String showStatus() {
        Runtime runtime = Runtime.getRuntime();
        long totalMem = runtime.totalMemory() / 1024 / 1024;
        long freeMem = runtime.freeMemory() / 1024 / 1024;
        long usedMem = totalMem - freeMem;
        return String.format(
                "系统状态\n运行内存: %dMB / %dMB\nCPU核心: %d\n",
                usedMem, totalMem, runtime.availableProcessors());
    }

    @Tool(name = "version", value = "查看项目版本号、Spring Boot版本、Java版本等技术信息")
    public String showVersion() {
        return String.format(
                "项目版本: 0.0.1-SNAPSHOT\nSpring Boot: %s\nJava: %s",
                org.springframework.boot.SpringBootVersion.getVersion(),
                System.getProperty("java.version"));
    }
}
