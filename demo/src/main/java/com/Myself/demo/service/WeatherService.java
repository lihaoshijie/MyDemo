package com.Myself.demo.service;

import com.Myself.demo.exception.CityNotFoundException;
import com.Myself.demo.exception.InvalidParameterException;
import com.Myself.demo.exception.WeatherApiException;
import com.Myself.demo.model.WeatherResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Slf4j
@Service
public class WeatherService {

    private final WebClient webClient;
    private final String apiKey;
    private final ObjectMapper objectMapper;

    public WeatherService(@Value("${seniverse.api.key}") String apiKey) {
        this.apiKey = apiKey;
        this.objectMapper = new ObjectMapper();
        this.webClient = WebClient.builder()
                .baseUrl("https://api.seniverse.com")
                .build();
        log.info("WeatherService 初始化完成");
    }

    // 实时天气
    public WeatherResponse getWeather(String city) {
        return callApi("/v3/weather/now.json", city, null);
    }

    // 预报天气，dayIndex: 0=今天, 1=明天, 2=后天
    public WeatherResponse getForecast(String city, int dayIndex) {
        return callApi("/v3/weather/daily.json", city, dayIndex);
    }

    // 多天预报，返回格式化的字符串
    public String getForecastMulti(String city, int apiDays, int showDays) {
        if (city == null || city.trim().isEmpty()) {
            throw new InvalidParameterException("城市名不能为空");
        }

        String location = city.trim();

        try {
            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v3/weather/daily.json")
                            .queryParam("key", apiKey)
                            .queryParam("location", location)
                            .queryParam("language", "zh-Hans")
                            .queryParam("unit", "c")
                            .queryParam("days", apiDays)
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.debug("预报API响应: {}", response);
            return parseMultiDayResponse(response, location, showDays);

        } catch (WebClientResponseException e) {
            log.error("天气API HTTP错误, 状态码: {}", e.getStatusCode(), e);
            throw new WeatherApiException("天气API调用失败: HTTP " + e.getStatusCode());
        } catch (WeatherApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("天气API调用异常", e);
            throw new WeatherApiException("天气API调用失败");
        }
    }

    private String parseMultiDayResponse(String response, String location, int showDays) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode results = root.path("results");
            if (!results.isArray() || results.isEmpty()) {
                throw new CityNotFoundException("城市不存在: " + location);
            }

            JsonNode weatherNode = results.get(0);
            String cityName = weatherNode.path("location").path("name").asText();
            JsonNode dailyArray = weatherNode.path("daily");
            if (!dailyArray.isArray() || dailyArray.isEmpty()) {
                throw new WeatherApiException("预报数据为空");
            }

            int count = Math.min(showDays, dailyArray.size());
            LocalDate today = LocalDate.now();
            DateTimeFormatter monthDayFmt = DateTimeFormatter.ofPattern("MM-dd");
            String[] weekDays = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};
            String[] dayLabels = {"今天", "明天", "后天"};

            StringBuilder sb = new StringBuilder();
            sb.append("【").append(cityName).append(" 未来").append(showDays).append("天天气】\n");

            for (int i = 0; i < count; i++) {
                JsonNode dayNode = dailyArray.get(i);
                LocalDate date = today.plusDays(i);
                String dateStr = date.format(monthDayFmt);
                String weekDay = weekDays[date.getDayOfWeek().getValue() % 7];
                String label = i < 3 ? " " + dayLabels[i] : "";
                String weather = dayNode.path("text_day").asText();
                String low = dayNode.path("low").asText();
                String high = dayNode.path("high").asText();

                sb.append("\n").append(dateStr).append(label).append(": ")
                  .append(weather).append(" ").append(low).append("~").append(high).append("°C");
            }

            log.info("多天预报查询成功: {} {}天", cityName, showDays);
            return sb.toString();

        } catch (CityNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("解析多天预报响应失败", e);
            throw new WeatherApiException("解析天气数据失败");
        }
    }

    private WeatherResponse callApi(String path, String city, Integer dayIndex) {
        if (city == null || city.trim().isEmpty()) {
            throw new InvalidParameterException("城市名不能为空");
        }

        String location = city.trim();

        try {
            String response = webClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path(path)
                                .queryParam("key", apiKey)
                                .queryParam("location", location)
                                .queryParam("language", "zh-Hans")
                                .queryParam("unit", "c");
                        if ("/v3/weather/daily.json".equals(path)) {
                            uriBuilder.queryParam("days", 3);
                        }
                        return uriBuilder.build();
                    })
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.debug("API 响应: {}", response);

            if ("/v3/weather/now.json".equals(path)) {
                return parseNowResponse(response, location);
            } else {
                return parseDailyResponse(response, location, dayIndex);
            }

        } catch (WebClientResponseException e) {
            log.error("天气API HTTP错误, 状态码: {}", e.getStatusCode(), e);
            throw new WeatherApiException("天气API调用失败: HTTP " + e.getStatusCode());
        } catch (WeatherApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("天气API调用异常", e);
            throw new WeatherApiException("天气API调用失败");
        }
    }

    private WeatherResponse parseNowResponse(String response, String location) {
        try {
            JsonNode root = objectMapper.readTree(response);
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
            weather.setFeelsLike(nowNode.path("feels_like").asText() + "°C");
            weather.setHumidity(nowNode.path("humidity").asText() + "%");
            weather.setWindDirection(nowNode.path("wind_direction").asText());
            weather.setWindSpeed(nowNode.path("wind_speed").asText() + "km/h");
            weather.setWindScale(nowNode.path("wind_scale").asText() + "级");
            weather.setVisibility(nowNode.path("visibility").asText() + "km");
            weather.setPressure(nowNode.path("pressure").asText() + "hPa");
            weather.setObservationTime(weatherNode.path("last_update").asText());

            log.info("实时天气查询成功: {}", weather.getCity());
            return weather;

        } catch (CityNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("解析实时天气响应失败", e);
            throw new WeatherApiException("解析天气数据失败");
        }
    }

    private WeatherResponse parseDailyResponse(String response, String location, int dayIndex) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode results = root.path("results");
            if (!results.isArray() || results.isEmpty()) {
                throw new CityNotFoundException("城市不存在: " + location);
            }

            JsonNode weatherNode = results.get(0);
            JsonNode dailyArray = weatherNode.path("daily");
            if (!dailyArray.isArray() || dailyArray.size() <= dayIndex) {
                throw new WeatherApiException("预报数据不足");
            }

            JsonNode dayNode = dailyArray.get(dayIndex);
            String[] dayLabels = {"今天", "明天", "后天"};

            WeatherResponse w = new WeatherResponse();
            JsonNode locationNode = weatherNode.path("location");
            w.setCity(locationNode.path("name").asText());
            String path = locationNode.path("path").asText();
            String[] pathParts = path.split(",");
            w.setProvince(pathParts.length >= 3 ? pathParts[pathParts.length - 2].trim() : "");
            w.setCountry(pathParts[pathParts.length - 1].trim());
            w.setWeather(dayNode.path("text_day").asText());
            w.setHigh(dayNode.path("high").asText() + "°C");
            w.setLow(dayNode.path("low").asText() + "°C");
            w.setWindDirection(dayNode.path("wind_direction").asText());
            w.setWindScale(dayNode.path("wind_scale").asText() + "级");
            w.setObservationTime(weatherNode.path("last_update").asText());

            log.info("预报天气查询成功: {} {}", w.getCity(), dayLabels[dayIndex]);
            return w;

        } catch (CityNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("解析预报天气响应失败", e);
            throw new WeatherApiException("解析天气数据失败");
        }
    }
}
