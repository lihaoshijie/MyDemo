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
public class BfrTool {

    @Value("${tianApi.key}")
    private String apiKey;

    private final WebClient webClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public BfrTool() {
        this.webClient = WebClient.builder()
                .baseUrl("https://apis.tianapi.com")
                .build();
    }

    @Tool(name = "body_fat_rate", value = "查询身材指数和体脂率。当用户问体脂率、身材指数、标准体重、减肥建议、健康风险时使用。需要提供年龄、身高、体重三个参数，缺一不可。性别可选，不说默认男性。")
    public String queryBfr(
            @P("年龄") Integer age,
            @P("身高，单位厘米cm") Integer height,
            @P("体重，单位千克kg") Integer weight,
            @P("性别，0=女性 1=男性，用户不说则默认1") Integer sex) {

        if (age == null || age <= 0) return "请告诉我你的年龄";
        if (height == null || height <= 0) return "请告诉我你的身高（厘米）";
        if (weight == null || weight <= 0) return "请告诉我你的体重（千克）";
        if (sex == null) sex = 1;
        final int s = sex;

        try {
            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/bfrsum/index")
                            .queryParam("key", apiKey)
                            .queryParam("age", age)
                            .queryParam("height", height)
                            .queryParam("weight", weight)
                            .queryParam("sex", s)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.debug("BFR API 响应: {}", response);
            JsonNode root = mapper.readTree(response);

            int code = root.path("code").asInt();
            if (code != 200) {
                String msg = root.path("msg").asText();
                return "查询失败：" + msg;
            }

            JsonNode result = root.path("result");
            return String.format(
                    "【身材指数】\n\n体脂率: %s\n正常体脂率范围: %s\n标准体重: %dkg\n正常体重范围: %skg\n健康风险: %s\n\n💡 %s",
                    result.path("bfr").asText(),
                    result.path("normbfr").asText(),
                    result.path("idealweight").asInt(),
                    result.path("normweight").asText(),
                    result.path("healthy").asText(),
                    result.path("tip").asText());

        } catch (Exception e) {
            log.error("BFR查询失败", e);
            return "身材指数查询失败，请稍后重试";
        }
    }
}
