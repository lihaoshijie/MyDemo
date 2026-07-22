package com.Myself.demo.tool;

import com.Myself.demo.service.MemoryService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

@Component
public class MemoryTool {

    private final MemoryService memoryService;

    public MemoryTool(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @Tool(name = "remember_fact", value = "记住用户的个人信息。当用户告诉你他的名字、生日、喜好、习惯等个人信息时，调用此工具保存。")
    public String rememberFact(
            @P("信息类别，如：名字、生日、喜好、职业") String key,
            @P("具体信息，如：托尼、5月20号、篮球、程序员") String value,
            @P("用户ID，系统自动填充") String userId) {
        if (key == null || key.isEmpty() || value == null || value.isEmpty()) {
            return "抱歉，我没记住";
        }
        memoryService.setFact(userId, key, value);
        return "好的，已记住你的" + key + "是" + value;
    }
}
