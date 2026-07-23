package com.Myself.demo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Service
public class NetworkHotService {

    private final WebClient webClient;
    private final String apiKey;
    private final ObjectMapper objectMapper;

    public NetworkHotService(@Value("${tianapi.api.key}") String apiKey) {
        this.apiKey = apiKey;
        this.objectMapper = new ObjectMapper();
        this.webClient = WebClient.builder()
                .baseUrl("https://apis.tianapi.com")
                .build();
        log.info("NetworkHotService 初始化完成");
    }

    public String getHotList() {
        try {
            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/networkhot/index")
                            .queryParam("key", apiKey)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.debug("全网热搜API响应: {}", response);
            return parseResponse(response);
        } catch (Exception e) {
            log.error("全网热搜API调用失败", e);
            return "热搜查询失败: " + e.getMessage();
        }
    }

    private String parseResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            int code = root.path("code").asInt();
            if (code != 200) {
                return "热搜查询失败: " + root.path("msg").asText();
            }

            JsonNode list = root.path("result").path("list");
            if (!list.isArray() || list.isEmpty()) {
                return "暂无热搜数据";
            }

            StringBuilder sb = new StringBuilder("【全网实时热搜榜单】\n");
            int rank = 0;
            for (JsonNode item : list) {
                rank++;
                String title = item.path("title").asText();
                int hotnum = item.path("hotnum").asInt();
                String digest = item.path("digest").asText();
                sb.append("\n").append(rank).append(". ").append(title);
                sb.append(" (").append(formatHotnum(hotnum)).append(")");
                if (!digest.isEmpty() && !"\"...\"".equals(digest) && !"...".equals(digest)) {
                    sb.append("\n   ").append(digest);
                }
            }

            log.info("全网热搜查询成功，共{}条", rank);
            return sb.toString();
        } catch (Exception e) {
            log.error("解析全网热搜响应失败", e);
            return "热搜查询失败: 数据解析异常";
        }
    }

    private String formatHotnum(int hotnum) {
        if (hotnum >= 10000) {
            return String.format("%.1f万", hotnum / 10000.0);
        }
        return String.valueOf(hotnum);
    }
}
