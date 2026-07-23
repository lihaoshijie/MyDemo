package com.Myself.demo.service;

import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesis;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisParam;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisResult;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.common.Role;
import com.Myself.demo.util.FileUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ImageService {

    private final String apiKey;

    public ImageService(@Value("${llm.api-key}") String apiKey) {
        this.apiKey = apiKey;
        log.info("ImageService 初始化完成");
    }

    public String recognizeImage(byte[] imageBytes, String question) {
        try {
            Path tempFile = FileUtil.createTempFile("wechat_img_", ".png", imageBytes);

            String q = question != null && !question.isEmpty() ? question : "描述图片内容";

            MultiModalConversation conv = new MultiModalConversation();
            MultiModalMessage userMsg = MultiModalMessage.builder()
                    .role(Role.USER.getValue())
                    .content(Arrays.asList(
                            Map.of("image", tempFile.toAbsolutePath().toString()),
                            Map.of("text", q)
                    ))
                    .build();

            MultiModalConversationParam param = MultiModalConversationParam.builder()
                    .apiKey(apiKey)
                    .model("qwen-vl-max")
                    .message(userMsg)
                    .build();

            MultiModalConversationResult result = conv.call(param);

            FileUtil.deleteTempFile(tempFile);

            String answer = extractTextResult(result);
            log.info("识图完成: question={}, answer={}", q, answer.substring(0, Math.min(50, answer.length())));
            return answer;

        } catch (Exception e) {
            log.error("识图失败", e);
            return "抱歉，图片识别失败，请稍后再试";
        }
    }

    public byte[] generateImage(String prompt) {
        try {
            ImageSynthesisParam param = ImageSynthesisParam.builder()
                    .apiKey(apiKey)
                    .model(ImageSynthesis.Models.WANX_V1)
                    .prompt(prompt)
                    .n(1)
                    .build();

            log.info("正在调用通义万相生成图片: {}", prompt);
            ImageSynthesisResult result = new ImageSynthesis().call(param);

            String taskStatus = result.getOutput().getTaskStatus();
            if (!"SUCCEEDED".equals(taskStatus)) {
                if ("RUNNING".equals(taskStatus) || "PENDING".equals(taskStatus)) {
                    log.info("图片生成任务进行中，等待完成...");
                    result = new ImageSynthesis().wait(result, apiKey);
                    taskStatus = result.getOutput().getTaskStatus();
                }
                if (!"SUCCEEDED".equals(taskStatus)) {
                    throw new RuntimeException("图片生成失败，状态: " + taskStatus
                            + ", 错误: " + result.getOutput().getMessage());
                }
            }

            String imageUrl = result.getOutput().getResults().get(0).get("url");
            log.info("图片生成成功，下载地址: {}", imageUrl);

            return downloadImage(imageUrl);

        } catch (Exception e) {
            log.error("生图失败", e);
            if (e.getMessage() != null && e.getMessage().contains("IPInfringement")) {
                throw new RuntimeException("该内容涉及版权保护，无法生成。请尝试其他非版权相关的内容。");
            }
            throw new RuntimeException("图片生成失败: " + e.getMessage(), e);
        }
    }

    private String extractTextResult(MultiModalConversationResult result) {
        List<Map<String, Object>> content = result.getOutput()
                .getChoices().get(0)
                .getMessage().getContent();
        for (Map<String, Object> item : content) {
            if (item.containsKey("text")) {
                return (String) item.get("text");
            }
        }
        return "无回复内容";
    }

    private byte[] downloadImage(String url) {
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(60000);
            try (InputStream in = conn.getInputStream();
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                }
                return out.toByteArray();
            }
        } catch (Exception e) {
            log.error("下载图片失败: {}", url, e);
            throw new RuntimeException("下载生成图片失败", e);
        }
    }
}
