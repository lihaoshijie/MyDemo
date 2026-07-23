package com.Myself.demo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
public class SciCalcService {

    private final WebClient webClient;
    private final String apiKey;
    private final ObjectMapper objectMapper;

    public SciCalcService(@Value("${tianapi.api.key}") String apiKey) {
        this.apiKey = apiKey;
        this.objectMapper = new ObjectMapper();
        this.webClient = WebClient.builder()
                .baseUrl("https://apis.tianapi.com")
                .build();
        log.info("SciCalcService 初始化完成");
    }

    public String calculate(String type, String num) {
        try {
            String encodedNum = URLEncoder.encode(num, StandardCharsets.UTF_8);
            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/scncalc/index")
                            .queryParam("key", apiKey)
                            .queryParam("type", type)
                            .queryParam("num", encodedNum)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.debug("科学计算API响应: type={}, num={}, resp={}", type, num, response);
            return parseResponse(response, type, num);
        } catch (Exception e) {
            log.error("科学计算API调用失败: type={}, num={}", type, num, e);
            return "计算失败: " + e.getMessage();
        }
    }

    private String parseResponse(String response, String type, String num) {
        try {
            JsonNode root = objectMapper.readTree(response);
            int code = root.path("code").asInt();
            if (code != 200) {
                return "计算失败: " + root.path("msg").asText();
            }

            JsonNode result = root.path("result");
            String tip = result.path("tip").asText();
            if (!"normal".equals(tip)) {
                return "计算失败: " + describeError(tip);
            }

            String value = result.path("value").asText();
            String opName = describeType(type);
            return String.format("%s(%s) = %s", opName, num, value);
        } catch (Exception e) {
            log.error("解析科学计算结果失败", e);
            return "计算失败: 数据解析异常";
        }
    }

    private String describeType(String type) {
        switch (type) {
            case "abs": return "绝对值";
            case "acos": return "反余弦";
            case "acosh": return "反双曲余弦";
            case "asin": return "反正弦";
            case "asinh": return "反双曲正弦";
            case "atan": return "反正切";
            case "atanh": return "反双曲正切";
            case "ceil": return "向上取整";
            case "cos": return "余弦";
            case "cosh": return "双曲余弦";
            case "cot": return "余切";
            case "deg2rad": return "角度转弧度";
            case "exp": return "自然指数";
            case "factorial": return "阶乘";
            case "fmod": return "浮点数取余";
            case "hypot": return "斜边长度";
            case "log": return "自然对数";
            case "mod": return "取余";
            case "percentage": return "百分比";
            case "pow": return "幂运算";
            case "rad2deg": return "弧度转角度";
            case "round": return "四舍五入";
            case "sin": return "正弦";
            case "sinh": return "双曲正弦";
            case "sqrt": return "平方根";
            case "tan": return "正切";
            case "tanh": return "双曲正切";
            default: return type;
        }
    }

    private String describeError(String tip) {
        switch (tip) {
            case "err_num": return "数字格式错误";
            case "lack_num": return "缺少必要数字参数";
            case "type_not": return "不支持的运算类型";
            default: return tip;
        }
    }
}
