package com.Myself.demo.exception;

public class WeatherApiException extends BusinessException {

    public WeatherApiException(String message) {
        super(ErrorCode.WEATHER_API_ERROR, message);
    }

    public WeatherApiException(String message, Throwable cause) {
        super(ErrorCode.WEATHER_API_ERROR, message);
        initCause(cause);
    }
}
