package com.Myself.demo.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class ChatService {

    private final int maxHistory;
    private final ConcurrentHashMap<String, List<Map<String, String>>> conversations = new ConcurrentHashMap<>();

    public ChatService(@Value("${llm.max-history:20}") int maxHistory) {
        this.maxHistory = maxHistory;
        log.info("ChatService 初始化完成, maxHistory: {}", maxHistory);
    }

    public List<Map<String, String>> getHistory(String userId) {
        return conversations.getOrDefault(userId, new ArrayList<>());
    }

    public void addHistory(String userId, String userMessage, String assistantReply) {
        List<Map<String, String>> history = conversations.computeIfAbsent(userId, k -> new ArrayList<>());

        history.add(Map.of("role", "user", "content", userMessage));
        history.add(Map.of("role", "assistant", "content", assistantReply));

        if (history.size() > maxHistory) {
            List<Map<String, String>> trimmed = new ArrayList<>(
                    history.subList(history.size() - maxHistory, history.size()));
            conversations.put(userId, trimmed);
        }
    }

    public void clearHistory(String userId) {
        conversations.remove(userId);
        log.info("清除对话历史: userId={}", userId);
    }
}
