package com.Myself.demo.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Component
public class AirQualityTool {

    @Value("${tianApi.key}")
    private String apiKey;

    private final WebClient webClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public AirQualityTool() {
        this.webClient = WebClient.builder()
                .baseUrl("https://apis.tianapi.com")
                .build();
    }

    @Tool(name = "air_quality", value = "查询指定城市的空气质量指数(AQI)和污染物数据。当用户问空气质量、空气好不好、雾霾、AQI、PM2.5时使用。如果用户同时问了天气，应当同时调用 weather 和 air_quality 两个工具。")
    public String queryAqi(
            @P("城市名称，如：北京、上海、杭州") String area) {

        if (area == null || area.trim().isEmpty()) {
            return "请告诉我你想查询哪个城市的空气质量";
        }

        try {
            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/aqi/index")
                            .queryParam("key", apiKey)
                            .queryParam("area", area.trim())
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.debug("AQI API 响应: {}", response);
            JsonNode root = mapper.readTree(response);

            int code = root.path("code").asInt();
            if (code != 200) {
                String msg = root.path("msg").asText();
                return "查询失败：" + msg;
            }

            JsonNode r = root.path("result");
            return String.format(
                    "【%s 空气质量】\n\nAQI: %s (%s)\nPM2.5: %s μg/m³\nPM10: %s μg/m³\nO₃: %s μg/m³\nNO₂: %s μg/m³\nSO₂: %s μg/m³\nCO: %s mg/m³\n首要污染物: %s\n\n🕐 %s",
                    r.path("area").asText(),
                    r.path("aqi").asText(),
                    r.path("quality").asText(),
                    r.path("pm2_5").asText(),
                    r.path("pm10").asText(),
                    r.path("o3").asText(),
                    r.path("no2").asText(),
                    r.path("so2").asText(),
                    r.path("co").asText(),
                    r.path("primary_pollutant").asText(),
                    r.path("time").asText());

        } catch (Exception e) {
            log.error("AQI查询失败", e);
            return "空气质量查询失败，请稍后重试";
        }
    }
}
