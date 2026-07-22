package com.Myself.demo.tool;

import com.Myself.demo.bot.ToolExecutionContext;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

@Component
public class FileTool {

    @Tool(name = "translate_file", value = "翻译已上传的文件内容。当用户要求翻译文件、把文件翻译成某种语言时，调用此工具。系统会自动读取已缓存的文件内容进行翻译，并以TXT文件形式返回。")
    public String translateFile(
            @P("目标语言，如：英语、日语、中文、韩语、法语、德语") String targetLanguage,
            @P("翻译要求，如：保留原文格式、专业术语保留原文") String instruction) {
        ToolExecutionContext.recordFileTranslate(targetLanguage, instruction);
        return "正在翻译文件...";
    }

    @Tool(name = "extract_from_file", value = "从已上传的文件中提取指定内容。当用户要求提取特定内容（如：提取所有问题、提取包含XX的段落、提取所有选择题）时，调用此工具。")
    public String extractFromFile(
            @P("要提取的内容关键词或描述，如：所有选择题、包含日期的段落、所有问题") String keyword,
            @P("输出格式，如：列表、原文、表格") String format) {
        ToolExecutionContext.recordFileExtract(keyword, format);
        return "正在提取文件内容...";
    }

    @Tool(name = "search_in_file", value = "在已上传的文件中搜索内容。当用户要求查找文件中的某个关键词、查找包含XX的内容时，调用此工具。")
    public String searchInFile(
            @P("搜索关键词") String query,
            @P("显示匹配行前后的上下文行数，默认2") int contextLines) {
        ToolExecutionContext.recordFileSearch(query, contextLines);
        return "正在搜索文件...";
    }

    @Tool(name = "export_file_summary", value = "导出文件总结/摘要为可下载的文件。当用户说总结、导出、生成文件、保存时，如果没有指定具体操作（如翻译），则调用此工具。如果用户有已上传的文件，此工具将生成总结并发送。")
    public String exportFileSummary() {
        ToolExecutionContext.recordFileExport();
        return "正在生成文件总结...";
    }
}
