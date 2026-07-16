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

        return "========== 程序状态 ==========\n" +
               "状态: 运行中\n" +
               "运行时间: " + hours + "h " + minutes + "m " + seconds + "s\n" +
               "内存使用: " + usedMemory + "MB / " + totalMemory + "MB\n" +
               "最大内存: " + maxMemory + "MB\n" +
               "可用处理器: " + runtime.availableProcessors() + " 核\n" +
               "==============================";
    }
}
