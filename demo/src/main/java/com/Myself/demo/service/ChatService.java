package com.Myself.demo.service;

import com.alibaba.dashscope.tokenizers.Tokenizer;
import com.alibaba.dashscope.tokenizers.TokenizerFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ChatService {

    private final LlmService llmService;
    private final int maxHistory;
    private final long tokenThreshold;

    @Autowired(required = false)
    private StringRedisTemplate redis;

    private final ConcurrentHashMap<String, List<Map<String, String>>> memoryFallback = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private Tokenizer tokenizer;

    private static final String REDIS_PREFIX = "chat:";
    private static final long REDIS_TTL = 24;

    public ChatService(LlmService llmService,
                       @Value("${llm.max-history:50}") int maxHistory,
                       @Value("${llm.token-threshold:100000}") long tokenThreshold) {
        this.llmService = llmService;
        this.maxHistory = maxHistory;
        this.tokenThreshold = tokenThreshold;
        try {
            this.tokenizer = TokenizerFactory.qwen();
        } catch (Exception e) {
            log.warn("Tokenizer 初始化失败，将使用字符估算", e);
        }
        log.info("ChatService 初始化完成, maxHistory={}, tokenThreshold={}", maxHistory, tokenThreshold);
    }

    private String redisKey(String userId) {
        return REDIS_PREFIX + userId;
    }

    public List<Map<String, String>> getHistory(String userId) {
        if (redis != null) {
            try {
                String json = redis.opsForValue().get(redisKey(userId));
                if (json != null && !json.isEmpty()) {
                    return objectMapper.readValue(json, List.class);
                }
            } catch (Exception e) {
                log.warn("Redis 读取失败，回退到内存: {}", e.getMessage());
            }
        }
        return memoryFallback.getOrDefault(userId, new ArrayList<>());
    }

    private void saveHistory(String userId, List<Map<String, String>> history) {
        if (redis != null) {
            try {
                String json = objectMapper.writeValueAsString(history);
                redis.opsForValue().set(redisKey(userId), json, REDIS_TTL, TimeUnit.HOURS);
                return;
            } catch (Exception e) {
                log.warn("Redis 写入失败，回退到内存: {}", e.getMessage());
            }
        }
        memoryFallback.put(userId, history);
    }

    public void addHistory(String userId, String userMessage, String assistantReply) {
        List<Map<String, String>> history = getHistory(userId);
        history.add(Map.of("role", "user", "content", userMessage));
        history.add(Map.of("role", "assistant", "content", assistantReply));
        checkAndCompress(userId, history);
    }

    private void checkAndCompress(String userId, List<Map<String, String>> history) {
        long tokens = estimateTokens(history);
        log.debug("对话 token 统计, userId={}, tokens={}, msgs={}", userId, tokens, history.size());

        if (history.size() <= maxHistory && tokens <= tokenThreshold) {
            saveHistory(userId, history);
            return;
        }

        log.info("触发历史压缩, userId={}, tokens={}, msgs={}, 阈值 tokens={}条数={}",
                userId, tokens, history.size(), tokenThreshold, maxHistory);

        int keepCount = Math.min(history.size() / 2, 10);
        int sumCount = history.size() - keepCount;

        List<Map<String, String>> toSummarize = new ArrayList<>(history.subList(0, sumCount));

        String summary = summarizeHistory(toSummarize);

        List<Map<String, String>> remaining = new ArrayList<>(history.subList(sumCount, history.size()));

        List<Map<String, String>> compressed = new ArrayList<>();
        if (summary != null && !summary.isEmpty()) {
            compressed.add(Map.of("role", "system", "content", "历史摘要：" + summary));
        }
        compressed.addAll(remaining);

        saveHistory(userId, compressed);
        log.info("历史压缩完成, userId={}, {}条→{}条, tokens={}→{}",
                userId, history.size(), compressed.size(),
                tokens, estimateTokens(compressed));
    }

    private String summarizeHistory(List<Map<String, String>> messages) {
        StringBuilder sb = new StringBuilder();
        for (Map<String, String> msg : messages) {
            String role = "user".equals(msg.get("role")) ? "用户" : "助手";
            String content = msg.get("content");
            if (content != null && content.length() > 80) {
                content = content.substring(0, 80) + "...";
            }
            sb.append(role).append("：").append(content).append("\n");
        }

        try {
            List<Map<String, String>> sumMsgs = new ArrayList<>();
            sumMsgs.add(Map.of("role", "user", "content",
                    "请将以下对话内容压缩为一段简短的历史摘要（80字以内），只保留关键信息：\n" + sb));
            String result = llmService.chat("_summarize", sumMsgs);
            if (result != null && result.length() > 200) {
                result = result.substring(0, 200);
            }
            return result;
        } catch (Exception e) {
            log.warn("摘要生成失败", e);
            return null;
        }
    }

    private long estimateTokens(List<Map<String, String>> history) {
        try {
            String fullText = history.stream()
                    .map(m -> m.get("content"))
                    .filter(s -> s != null)
                    .collect(Collectors.joining(" "));
            if (fullText.isEmpty()) return 0;
            if (tokenizer != null) {
                return tokenizer.encodeOrdinary(fullText).size();
            }
        } catch (Exception e) {
            log.warn("Tokenizer 调用失败，使用字符估算", e);
        }
        long chars = 0;
        for (Map<String, String> m : history) {
            String c = m.get("content");
            if (c != null) chars += c.length();
        }
        return chars;
    }

    public void clearHistory(String userId) {
        if (redis != null) {
            redis.delete(redisKey(userId));
        }
        memoryFallback.remove(userId);
        log.info("清除对话历史: userId={}", userId);
    }

    public String getLastUserQuestion(String userId) {
        List<Map<String, String>> history = getHistory(userId);
        for (int i = history.size() - 1; i >= 0; i--) {
            Map<String, String> msg = history.get(i);
            if ("user".equals(msg.get("role"))) {
                String content = msg.get("content");
                if (content != null && !content.startsWith("[重新识别图片]")) {
                    return content;
                }
            }
        }
        return null;
    }

    public String chat(String userId, String userMessage) {
        List<Map<String, String>> history = getHistory(userId);
        history.add(Map.of("role", "user", "content", userMessage));
        checkAndCompress(userId, history);
        history = getHistory(userId);

        String reply = llmService.chat(userId, history);
        history.add(Map.of("role", "assistant", "content", reply));
        checkAndCompress(userId, history);

        log.info("AI 对话: userId={}", userId);
        return reply;
    }
}
