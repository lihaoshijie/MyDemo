package com.Myself.demo.command;

public interface Command {
    String getName();
    String execute(String[] args);
}
