package com.Myself.demo.command;

import org.springframework.stereotype.Component;
import java.lang.management.ManagementFactory;

@Component
public class StatusCommand implements Command {

    @Override
    public String getName() {
        return "status";
    }

    @Override
    public String execute(String[] args) {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory() / (1024 * 1024);
        long freeMemory = runtime.freeMemory() / (1024 * 1024);
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = runtime.maxMemory() / (1024 * 1024);

        long uptime = ManagementFactory.getRuntimeMXBean().getUptime();
        long hours = uptime / (1000 * 60 * 60);
        long minutes = (uptime % (1000 * 60 * 60)) / (1000 * 60);
        long seconds = (uptime % (1000 * 60)) / 1000;

        return "【程序状态】运行中\n\n" +
               "运行时间: " + hours + "h " + minutes + "m " + seconds + "s\n\n" +
               "内存: " + usedMemory + "MB / " + totalMemory + "MB\n\n" +
               "最大内存: " + maxMemory + "MB\n\n" +
               "处理器: " + runtime.availableProcessors() + "核";
    }
}
