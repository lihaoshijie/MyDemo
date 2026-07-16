package com.Myself.demo.command;

import com.Myself.demo.exception.MissingParameterException;
import com.Myself.demo.model.WeatherResponse;
import com.Myself.demo.service.WeatherService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class WeatherCommand implements Command {

    @Autowired
    private WeatherService weatherService;

    @Override
    public String getName() {
        return "weather";
    }

    @Override
    public String execute(String[] args) {
        if (args.length == 0 || args[0].trim().isEmpty()) {
            throw new MissingParameterException("缺少城市参数，用法: weather <城市名>");
        }

        String city = args[0].trim();
        log.info("查询城市天气: {}", city);

        WeatherResponse weather = weatherService.getWeather(city);

        return "【" + weather.getCity() + "天气】\n\n" +
               "地区: " + weather.getProvince() + ", " + weather.getCountry() + "\n\n" +
               "天气: " + weather.getWeather() + "\n\n" +
               "温度: " + weather.getTemperature() + "\n\n" +
               "湿度: " + weather.getHumidity() + "\n\n" +
               "风向: " + weather.getWindDirection() + " " + weather.getWindScale();
    }
}
