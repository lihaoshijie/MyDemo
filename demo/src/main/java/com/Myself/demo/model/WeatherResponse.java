package com.Myself.demo.model;

import lombok.Data;

@Data
public class WeatherResponse {
    private String city;
    private String province;
    private String country;
    private String district;
    private String weather;
    private String temperature;
    private String feelsLike;
    private String high;
    private String low;
    private String humidity;
    private String windDirection;
    private String windSpeed;
    private String windScale;
    private String visibility;
    private String pressure;
    private String observationTime;
}
