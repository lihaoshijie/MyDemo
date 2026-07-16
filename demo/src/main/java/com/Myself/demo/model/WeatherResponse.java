package com.Myself.demo.model;

import lombok.Data;

@Data
public class WeatherResponse {
    private String city;
    private String weather;
    private String temperature;
    private String humidity;
    private String windDirection;
    private String windScale;
    private String observationTime;
}
