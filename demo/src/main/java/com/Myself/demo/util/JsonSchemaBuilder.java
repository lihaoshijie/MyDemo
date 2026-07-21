package com.Myself.demo.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public class JsonSchemaBuilder {

    private final JsonObject schema = new JsonObject();
    private final JsonObject properties = new JsonObject();
    private final JsonArray required = new JsonArray();

    public JsonSchemaBuilder() {
        schema.addProperty("type", "object");
    }

    public JsonSchemaBuilder addString(String name, String description, boolean isRequired) {
        JsonObject prop = new JsonObject();
        prop.addProperty("type", "string");
        prop.addProperty("description", description);
        properties.add(name, prop);
        if (isRequired) required.add(new JsonPrimitive(name));
        return this;
    }

    public JsonSchemaBuilder addInteger(String name, String description, boolean isRequired) {
        JsonObject prop = new JsonObject();
        prop.addProperty("type", "integer");
        prop.addProperty("description", description);
        properties.add(name, prop);
        if (isRequired) required.add(new JsonPrimitive(name));
        return this;
    }

    public JsonSchemaBuilder addEnum(String name, String description, int... values) {
        JsonObject prop = new JsonObject();
        prop.addProperty("type", "integer");
        prop.addProperty("description", description);
        JsonArray enums = new JsonArray();
        for (int v : values) enums.add(new JsonPrimitive(v));
        prop.add("enum", enums);
        properties.add(name, prop);
        return this;
    }

    public JsonObject build() {
        schema.add("properties", properties);
        schema.add("required", required);
        return schema;
    }
}
