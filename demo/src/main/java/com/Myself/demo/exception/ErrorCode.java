package com.Myself.demo.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {
    MISSING_PARAMETER(1001, "缺少必要参数"),
    INVALID_PARAMETER(1002, "参数格式错误"),
    CITY_NOT_FOUND(1003, "城市不存在"),
    WEATHER_API_ERROR(1004, "天气API调用失败"),
    SYSTEM_ERROR(1005, "系统异常");

    private final int code;
    private final String message;
}
