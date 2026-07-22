package com.Myself.demo.tool;

import com.Myself.demo.bot.ToolExecutionContext;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

@Component
public class ImageTool {

    @Tool(name = "generate_image", value = "从零生成一张全新的图片。当用户说'画'、'生成'、'制作'等，且不涉及修改已有图片时使用。注意：如果用户的请求涉及已有图片的操作（如合并、融合、变风格、P掉、移除），请不要使用此工具，改用 transform_image。")
    public String generateImage(
            @P("图片生成提示词，如：一只可爱的猫") String prompt) {
        ToolExecutionContext.recordImageGen(prompt);
        return "正在生成图片中...";
    }

    @Tool(name = "transform_image", value = "基于用户已发送的图片进行变换、编辑、合并或融合。当用户的请求涉及对已有图片的操作时，使用此工具。系统会自动把原图传给AI作为参考。注意：不要在回复中生成工具名称文字，请直接调用此工具。")
    public String transformImage(
            @P("图片变换提示词，如：把这张图变成卡通风格") String prompt) {
        ToolExecutionContext.recordImageTransform(prompt);
        return "正在处理图片中...";
    }

    @Tool(name = "re_examine_image", value = "重新仔细观察已发送的图片。当用户说'再看看'、'重新看看'、'仔细看看'、'不对'、'看不清楚'等需要重新识别图片时使用。")
    public String reExamineImage(
            @P("用户具体想看的内容或问题，如：图片中有什么文字、这个图标是什么意思") String question) {
        ToolExecutionContext.recordReExamine(question);
        return "正在重新识别图片...";
    }
}
