package com.Myself.demo;

import com.Myself.demo.bot.WeChatBotService;
import com.Myself.demo.cli.CommandExecutor;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Component;

@Slf4j
@SpringBootApplication
@MapperScan("com.Myself.demo.mapper")
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    @Component
    static class AppRunner implements CommandLineRunner {

        private final CommandExecutor commandExecutor;
        private final WeChatBotService weChatBotService;

        AppRunner(CommandExecutor commandExecutor, WeChatBotService weChatBotService) {
            this.commandExecutor = commandExecutor;
            this.weChatBotService = weChatBotService;
        }

        @Override
        public void run(String... args) {
            log.info("应用启动完成，启动 CLI 和微信 Bot");
            weChatBotService.start();
            commandExecutor.start();
        }
    }
}
