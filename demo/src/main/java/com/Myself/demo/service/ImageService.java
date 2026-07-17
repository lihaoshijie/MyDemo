package com.Myself.demo.service;

import com.alibaba.dashscope.aigc.imagegeneration.ImageGeneration;
import com.alibaba.dashscope.aigc.imagegeneration.ImageGenerationParam;
import com.alibaba.dashscope.aigc.imagegeneration.ImageGenerationResult;
import com.alibaba.dashscope.aigc.imagegeneration.ImageGenerationMessage;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.common.Role;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
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
            Path tempFile = Files.createTempFile("wechat_img_", ".png");
            Files.write(tempFile, imageBytes);

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

            Files.deleteIfExists(tempFile);

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
            ImageGeneration ig = new ImageGeneration();
            ImageGenerationMessage msg = ImageGenerationMessage.builder()
                    .role(Role.USER.getValue())
                    .content(Collections.singletonList(
                            Map.of("text", prompt)
                    ))
                    .build();

            ImageGenerationParam param = ImageGenerationParam.builder()
                    .apiKey(apiKey)
                    .model("wan2.6-t2i")
                    .n(1)
                    .size("1024*1024")
                    .messages(Collections.singletonList(msg))
                    .build();

            ImageGenerationResult result = ig.call(param);

            String imageUrl = extractImageUrl(result);
            if (imageUrl == null || imageUrl.isEmpty()) {
                imageUrl = waitForAsyncResult(ig, result);
            }
            if (imageUrl == null || imageUrl.isEmpty()) {
                throw new RuntimeException("未能获取生成图片的URL");
            }

            log.info("生图完成: prompt={}, url={}", prompt, imageUrl);
            return downloadImage(imageUrl);

        } catch (Exception e) {
            log.error("生图失败", e);
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

    private String extractImageUrl(ImageGenerationResult result) {
        if (result.getOutput() == null) return null;
        if (result.getOutput().getChoices() != null) {
            List<Map<String, Object>> content = result.getOutput()
                    .getChoices().get(0)
                    .getMessage().getContent();
            for (Map<String, Object> item : content) {
                if (item.containsKey("image")) {
                    return (String) item.get("image");
                }
            }
        }
        return null;
    }

    private String waitForAsyncResult(ImageGeneration ig, ImageGenerationResult result) {
        try {
            String taskId = result.getOutput().getTaskId();
            if (taskId == null) return null;
            log.info("等待生图异步任务完成: taskId={}", taskId);
            ImageGenerationResult finalResult = ig.wait(taskId, apiKey);
            return extractImageUrl(finalResult);
        } catch (Exception e) {
            log.error("等待生图异步结果失败", e);
            return null;
        }
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
