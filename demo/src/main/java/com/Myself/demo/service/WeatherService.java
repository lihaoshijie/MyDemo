package com.Myself.demo.service;

import com.Myself.demo.exception.CityNotFoundException;
import com.Myself.demo.exception.InvalidParameterException;
import com.Myself.demo.exception.WeatherApiException;
import com.Myself.demo.model.WeatherResponse;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Slf4j
@Service
public class WeatherService {

    private final WebClient webClient;
    private final String apiKey;

    public WeatherService(@Value("${seniverse.api.key}") String apiKey) {
        this.apiKey = apiKey;
        this.webClient = WebClient.builder()
                .baseUrl("https://api.seniverse.com")
                .build();
        log.info("WeatherService 初始化完成, API Key: {}...", apiKey.substring(0, Math.min(8, apiKey.length())));
    }

    public WeatherResponse getWeather(String city) {
        if (city == null || city.trim().isEmpty()) {
            throw new InvalidParameterException("城市名不能为空");
        }

        String location = city.trim();
        log.info("开始查询天气, 城市: {}", location);

        try {
            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v3/weather/now.json")
                            .queryParam("key", apiKey)
                            .queryParam("location", location)
                            .queryParam("language", "zh-Hans")
                            .queryParam("unit", "c")
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.debug("API 响应: {}", response);

            return parseResponse(response, location);

        } catch (WebClientResponseException e) {
            log.error("天气API HTTP错误, 状态码: {}", e.getStatusCode(), e);
            throw new WeatherApiException("天气API调用失败: HTTP " + e.getStatusCode());
        } catch (WeatherApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("天气API调用异常", e);
            throw new WeatherApiException("天气API调用失败: " + e.getMessage(), e);
        }
    }

    private WeatherResponse parseResponse(String response, String location) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            JsonNode root = mapper.readTree(response);

            JsonNode results = root.path("results");
            if (!results.isArray() || results.isEmpty()) {
                throw new CityNotFoundException("城市不存在: " + location);
            }

            JsonNode weatherNode = results.get(0);
            JsonNode nowNode = weatherNode.path("now");

            WeatherResponse weather = new WeatherResponse();
            JsonNode locationNode = weatherNode.path("location");
            weather.setCity(locationNode.path("name").asText());
            String path = locationNode.path("path").asText();
            String[] pathParts = path.split(",");
            weather.setProvince(pathParts.length >= 3 ? pathParts[pathParts.length - 2].trim() : "");
            weather.setCountry(pathParts[pathParts.length - 1].trim());
            weather.setWeather(nowNode.path("text").asText());
            weather.setTemperature(nowNode.path("temperature").asText() + "°C");
            weather.setHumidity(nowNode.path("humidity").asText() + "%");
            weather.setWindDirection(nowNode.path("wind_direction").asText());
            weather.setWindScale(nowNode.path("wind_scale").asText() + "级");
            weather.setObservationTime(weatherNode.path("last_update").asText());

            log.info("天气查询成功: {} - {}", weather.getCity(), weather.getWeather());
            return weather;

        } catch (CityNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("解析天气响应失败", e);
            throw new WeatherApiException("解析天气数据失败: " + e.getMessage());
        }
    }
}
