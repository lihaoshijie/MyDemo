package com.Myself.demo.tool;

import com.Myself.demo.model.WeatherResponse;
import com.Myself.demo.service.WeatherService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class WeatherTool {

    private final WeatherService weatherService;

    public WeatherTool(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    @Tool(name = "weather", value = "查询全球任意城市的实时天气或未来天气预报。支持所有城市，包括国内和国际城市（如北京、纽约、东京、伦敦等）。只支持实时天气和未来几天的预报，不支持查询历史天气（昨天、前天等）。对于主观天气感受（如热不热、冷不冷）或历史天气，请直接回答，不要调用此工具。")
    public String queryWeather(
            @P("城市名称，如：北京、上海、杭州") String city,
            @P("查询未来几天。1=实时，3=三天，7=七天，15=十五天。默认1") int days) {
        return doQuery(city, days);
    }

    private String doQuery(String city, int days) {
        try {
            if (days <= 1) {
                WeatherResponse w = weatherService.getWeather(city);
                return String.format(
                        "🌤 %s 实时天气\n\n天气: %s  温度: %s\n体感: %s  湿度: %s\n风向: %s  风速: %s\n风力: %s  能见度: %s\n气压: %s\n\n🕐 %s",
                        w.getCity(), w.getWeather(), w.getTemperature(),
                        w.getFeelsLike(), w.getHumidity(), w.getWindDirection(),
                        w.getWindSpeed(), w.getWindScale(), w.getVisibility(),
                        w.getPressure(), w.getObservationTime());
            } else {
                int apiDays = days <= 3 ? 3 : days <= 7 ? 7 : 15;
                return weatherService.getForecastMulti(city, apiDays, days);
            }
        } catch (Exception e) {
            return "天气查询失败: " + e.getMessage();
        }
    }
}
