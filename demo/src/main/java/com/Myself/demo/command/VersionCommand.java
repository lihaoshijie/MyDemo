package com.Myself.demo.command;

import org.springframework.stereotype.Component;

@Component
public class VersionCommand implements Command {

    @Override
    public String getName() {
        return "version";
    }

    @Override
    public String execute(String[] args) {
        String javaVersion = System.getProperty("java.version");
        return "项目版本: 0.0.1-SNAPSHOT\n" +
               "Spring Boot: 4.0.7\n" +
               "Java: " + javaVersion;
    }
}
