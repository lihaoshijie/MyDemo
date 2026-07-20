package com.Myself.demo.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class MemoryService {

    private final ConcurrentHashMap<String, ConcurrentHashMap<String, String>> memories = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String getFormattedFacts(String userId) {
        ConcurrentHashMap<String, String> facts = getOrLoad(userId);
        if (facts.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        facts.forEach((k, v) -> sb.append(k).append("是").append(v).append("，"));
        return sb.substring(0, sb.length() - 1);
    }

    public void setFact(String userId, String key, String value) {
        ConcurrentHashMap<String, String> facts = getOrLoad(userId);
        facts.put(key, value);
        save(userId, facts);
        log.info("记忆已保存: userId={}, {}={}", userId, key, value);
    }

    private ConcurrentHashMap<String, String> getOrLoad(String userId) {
        return memories.computeIfAbsent(userId, id -> {
            File file = new File("memory-" + id + ".json");
            if (file.exists()) {
                try {
                    Map<String, String> loaded = objectMapper.readValue(file,
                            new TypeReference<Map<String, String>>() {});
                    return new ConcurrentHashMap<>(loaded);
                } catch (Exception e) {
                    log.warn("加载记忆文件失败: {}", e.getMessage());
                }
            }
            return new ConcurrentHashMap<>();
        });
    }

    private void save(String userId, ConcurrentHashMap<String, String> facts) {
        try {
            objectMapper.writeValue(new File("memory-" + userId + ".json"), facts);
        } catch (Exception e) {
            log.warn("保存记忆文件失败: {}", e.getMessage());
        }
    }
}
