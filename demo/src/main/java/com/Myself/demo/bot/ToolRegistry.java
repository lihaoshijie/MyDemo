package com.Myself.demo.bot;

import com.alibaba.dashscope.tools.FunctionDefinition;
import com.alibaba.dashscope.tools.ToolFunction;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class ToolRegistry {

    private final ApplicationContext applicationContext;
    private final Map<String, ToolEntry> tools = new HashMap<>();
    private volatile List<ToolFunction> cachedTools;

    public ToolRegistry(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @PostConstruct
    public void init() {
        tools.clear();
        String[] beanNames = applicationContext.getBeanDefinitionNames();
        for (String beanName : beanNames) {
            Class<?> beanType = applicationContext.getType(beanName);
            if (beanType == null) continue;
            for (Method method : beanType.getDeclaredMethods()) {
                Tool toolAnno = method.getAnnotation(Tool.class);
                if (toolAnno == null) continue;

                String toolName = toolAnno.name();
                if (toolName == null || toolName.isEmpty()) {
                    toolName = method.getName();
                }
                String description = toolAnno.value().length > 0
                        ? String.join(" ", toolAnno.value())
                        : method.getName();

                Parameter[] params = method.getParameters();
                List<ParamDef> paramDefs = new ArrayList<>();

                for (Parameter param : params) {
                    P pAnno = param.getAnnotation(P.class);
                    if (pAnno == null) continue;
                    String paramName = param.getName();
                    String paramDesc = pAnno.value();
                    Class<?> paramType = param.getType();
                    boolean isSystem = "userId".equals(paramName);

                    paramDefs.add(new ParamDef(paramName, paramDesc, paramType, isSystem));
                }

                if (tools.containsKey(toolName)) {
                    log.warn("同名工具已存在，跳过: {}", toolName);
                    continue;
                }

                tools.put(toolName, new ToolEntry(toolName, description, beanName, method, paramDefs));
                log.info("注册工具: {} ({})", toolName, beanType.getSimpleName());
            }
        }
    }

    public List<ToolFunction> buildTools() {
        if (cachedTools != null) return cachedTools;
        List<ToolFunction> result = new ArrayList<>();
        for (ToolEntry entry : tools.values()) {
            ToolFunction tf = ToolFunction.builder()
                    .function(FunctionDefinition.builder()
                            .name(entry.name)
                            .description(entry.description)
                            .parameters(buildParams(entry))
                            .build())
                    .build();
            result.add(tf);
        }
        cachedTools = result;
        return cachedTools;
    }

    private JsonObject buildParams(ToolEntry entry) {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject properties = new JsonObject();
        JsonArray required = new JsonArray();

        for (ParamDef pd : entry.paramDefs) {
            if (pd.systemParam) continue;

            JsonObject prop = new JsonObject();
            if (pd.type == String.class) {
                prop.addProperty("type", "string");
            } else if (pd.type == int.class || pd.type == Integer.class) {
                prop.addProperty("type", "integer");
            } else if (pd.type == double.class || pd.type == Double.class) {
                prop.addProperty("type", "number");
            } else if (pd.type == boolean.class || pd.type == Boolean.class) {
                prop.addProperty("type", "boolean");
            } else {
                prop.addProperty("type", "string");
            }
            prop.addProperty("description", pd.description);
            properties.add(pd.name, prop);
            required.add(new JsonPrimitive(pd.name));
        }

        schema.add("properties", properties);
        schema.add("required", required);
        return schema;
    }

    public String execute(String toolName, String llmArgs, String userId) {
        ToolEntry entry = tools.get(toolName);
        if (entry == null) return null;

        try {
            List<Object> callArgs = new ArrayList<>();
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode argsNode = mapper.readTree(llmArgs);

            for (ParamDef pd : entry.paramDefs) {
                if (pd.systemParam) {
                    if ("userId".equals(pd.name)) {
                        callArgs.add(userId);
                    } else {
                        callArgs.add(null);
                    }
                } else {
                    if (pd.type == String.class) {
                        callArgs.add(argsNode.has(pd.name) ? argsNode.get(pd.name).asText() : "");
                    } else if (pd.type == int.class || pd.type == Integer.class) {
                        callArgs.add(argsNode.has(pd.name) ? argsNode.get(pd.name).asInt() : 0);
                    } else if (pd.type == double.class || pd.type == Double.class) {
                        callArgs.add(argsNode.has(pd.name) ? argsNode.get(pd.name).asDouble() : 0.0);
                    } else if (pd.type == boolean.class || pd.type == Boolean.class) {
                        callArgs.add(argsNode.has(pd.name) ? argsNode.get(pd.name).asBoolean() : false);
                    } else {
                        callArgs.add(argsNode.has(pd.name) ? argsNode.get(pd.name).asText() : "");
                    }
                }
            }

            Object bean = applicationContext.getBean(entry.beanName);
            return (String) entry.method.invoke(bean, callArgs.toArray());
        } catch (Exception e) {
            log.error("工具执行失败: {}", toolName, e);
            return "工具执行失败";
        }
    }

    private static class ToolEntry {
        final String name;
        final String description;
        final String beanName;
        final Method method;
        final List<ParamDef> paramDefs;

        ToolEntry(String name, String description, String beanName, Method method, List<ParamDef> paramDefs) {
            this.name = name;
            this.description = description;
            this.beanName = beanName;
            this.method = method;
            this.paramDefs = paramDefs;
        }
    }

    private static class ParamDef {
        final String name;
        final String description;
        final Class<?> type;
        final boolean systemParam;

        ParamDef(String name, String description, Class<?> type, boolean systemParam) {
            this.name = name;
            this.description = description;
            this.type = type;
            this.systemParam = systemParam;
        }
    }
}
