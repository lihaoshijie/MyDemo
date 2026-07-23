package com.Myself.demo.command;

import com.Myself.demo.exception.MissingParameterException;
import com.Myself.demo.model.WeatherResponse;
import com.Myself.demo.service.WeatherService;
import com.Myself.demo.service.SmartWeatherService;
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
            throw new MissingParameterException("缺少城市参数，用法: weather <城市名> [天数]");
        }

        String city = args[0].trim();
        int days = 1;
        if (args.length > 1) {
            try {
                days = Integer.parseInt(args[1].trim());
            } catch (NumberFormatException e) {
                days = 1;
            }
        }

        if (days <= 1) {
            log.info("查询实时天气: {}", city);
            WeatherResponse w = weatherService.getWeather(city);
            return SmartWeatherService.formatSmartReport(w);
        }

        int apiDays = days <= 3 ? 3 : days <= 7 ? 7 : 15;
        log.info("查询预报天气: {} 天数={}", city, apiDays);
        return weatherService.getForecastMulti(city, apiDays, days);
    }

}
