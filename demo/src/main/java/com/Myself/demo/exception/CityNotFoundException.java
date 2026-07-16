package com.Myself.demo.exception;

public class CityNotFoundException extends BusinessException {

    public CityNotFoundException(String message) {
        super(ErrorCode.CITY_NOT_FOUND, message);
    }
}
