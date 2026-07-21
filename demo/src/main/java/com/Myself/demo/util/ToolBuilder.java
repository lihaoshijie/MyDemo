package com.Myself.demo.util;

import com.alibaba.dashscope.tools.FunctionDefinition;
import com.alibaba.dashscope.tools.ToolFunction;

public class ToolBuilder {

    private final String name;
    private final String description;
    private final JsonSchemaBuilder schemaBuilder = new JsonSchemaBuilder();

    public ToolBuilder(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public ToolBuilder addString(String name, String description, boolean required) {
        schemaBuilder.addString(name, description, required);
        return this;
    }

    public ToolBuilder addInteger(String name, String description, boolean required) {
        schemaBuilder.addInteger(name, description, required);
        return this;
    }

    public ToolBuilder addEnum(String name, String description, int... values) {
        schemaBuilder.addEnum(name, description, values);
        return this;
    }

    public ToolFunction build() {
        return ToolFunction.builder()
                .function(FunctionDefinition.builder()
                        .name(name)
                        .description(description)
                        .parameters(schemaBuilder.build())
                        .build())
                .build();
    }

    public static ToolFunction simple(String name, String description) {
        return ToolFunction.builder()
                .function(FunctionDefinition.builder()
                        .name(name)
                        .description(description)
                        .build())
                .build();
    }
}
