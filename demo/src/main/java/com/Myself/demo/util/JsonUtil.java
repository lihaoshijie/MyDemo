package com.Myself.demo.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public static String toJson(Object obj) {
        try { return MAPPER.writeValueAsString(obj); }
        catch (JsonProcessingException e) { throw new RuntimeException(e); }
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        try { return MAPPER.readValue(json, clazz); }
        catch (JsonProcessingException e) { throw new RuntimeException(e); }
    }

    public static <T> T fromJson(String json, TypeReference<T> type) {
        try { return MAPPER.readValue(json, type); }
        catch (JsonProcessingException e) { throw new RuntimeException(e); }
    }

    public static String prettyPrint(Object obj) {
        try { return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(obj); }
        catch (JsonProcessingException e) { throw new RuntimeException(e); }
    }
}
