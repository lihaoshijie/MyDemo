package com.Myself.demo.bot;

import com.Myself.demo.service.ImageService;
import com.Myself.demo.service.VoiceService;
import com.Myself.demo.service.VoicePreferenceService;
import com.Myself.demo.util.FileUtil;
import com.Myself.demo.util.TimeUtil;
import com.alibaba.dashscope.utils.OSSUtils;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import com.github.wechat.ilink.sdk.ILinkClient;
import com.github.wechat.ilink.sdk.core.config.ILinkConfig;
import com.github.wechat.ilink.sdk.core.listener.OnLoginListener;
import com.github.wechat.ilink.sdk.core.listener.OnMessageListener;
import com.github.wechat.ilink.sdk.core.login.LoginContext;
import com.github.wechat.ilink.sdk.core.model.MessageItem;
import com.github.wechat.ilink.sdk.core.model.WeixinMessage;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.file.Path;

@Slf4j
@Service
public class WeChatBotService {

    @Autowired
    private CommandRouter commandRouter;

    @Autowired
    private ImageService imageService;

    @Autowired
    private com.Myself.demo.service.ChatService chatService;

    @Autowired
    private VoiceService voiceService;

    @Autowired
    private VoicePreferenceService voicePreferenceService;

    @Autowired
    private com.Myself.demo.service.LlmService llmService;

    @Value("${llm.api-key}")
    private String apiKey;

    private ILinkClient client;
    private static final long IMAGE_BUFFER_TTL = 5 * 60 * 1000;
    private static final int MAX_IMAGES = 5;

    private final ConcurrentHashMap<String, List<ImageEntry>> imageBuffers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> lastFileResult = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> lastFileContent = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> lastFileName = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> voiceModeTime = new ConcurrentHashMap<>();
    private static final long VOICE_MODE_TTL = 5 * 60 * 1000;

    private static class ImageEntry {
        byte[] bytes;
        String desc;
        long timestamp;

        ImageEntry(byte[] bytes, String desc) {
            this.bytes = bytes;
            this.desc = desc;
            this.timestamp = TimeUtil.nowMillis();
        }
    }

    public void start() {
        ILinkConfig config = ILinkConfig.builder()
                .connectTimeoutMs(35000)
                .readTimeoutMs(35000)
                .writeTimeoutMs(35000)
                .heartbeatEnabled(true)
                .heartbeatIntervalMs(5000)
                .channelVersion("1.0.0")
                .build();

        LoginContext savedContext = SessionManager.load();

        if (savedContext != null) {
            if (promptAutoLogin()) {
                startWithSession(config, savedContext);
            } else {
                SessionManager.clear();
                startWithQR(config);
            }
        } else {
            startWithQR(config);
        }
    }

    private boolean promptAutoLogin() {
        System.out.println("=========================================");
        System.out.println("检测到历史登录会话，将在 3 秒后自动登录");
        System.out.println("如需切换账号，请按 Enter 键跳过自动登录");
        System.out.println("=========================================");
        try {
            if (System.in.available() > 0) {
                System.in.read(new byte[System.in.available()]);
            }
            long deadline = TimeUtil.afterMillis(3000);
            while (!TimeUtil.isPast(deadline)) {
                if (System.in.available() > 0) {
                    System.in.read(new byte[System.in.available()]);
                    System.out.println("已取消自动登录，进入扫码模式");
                    return false;
                }
                Thread.sleep(100);
            }
        } catch (Exception e) {
            log.warn("等待用户输入异常", e);
        }
        return true;
    }

    private void startWithSession(ILinkConfig config, LoginContext savedContext) {
        log.info("尝试使用历史会话恢复登录...");

        client = ILinkClient.builder()
                .config(config)
                .loginContext(savedContext)
                .onMessage(new OnMessageListener() {
                    @Override
                    public void onMessages(List<WeixinMessage> messages) {
                        handleMessages(messages);
                    }
                })
                .build();

        if (client.isLoggedIn()) {
            log.info("历史会话恢复成功，已自动登录");
            System.out.println("✓ 微信 Bot 自动登录成功! 无需扫码");
        } else {
            log.warn("历史会话恢复失败，切换到扫码模式");
            SessionManager.clear();
            startWithQR(config);
        }
    }

    private void startWithQR(ILinkConfig config) {
        client = ILinkClient.builder()
                .config(config)
                .onLogin(new OnLoginListener() {
                    @Override
                    public void onLoginSuccess(LoginContext context) {
                        log.info("iLink 登录成功, botId = {}", context.getBotId());
                        System.out.println("✓ 微信 Bot 登录成功!");
                        SessionManager.save(context);
                    }

                    @Override
                    public void onLoginFailure(Throwable throwable) {
                        log.error("iLink 登录失败", throwable);
                        System.out.println("✗ 微信 Bot 登录失败: " + throwable.getMessage());
                        SessionManager.clear();
                    }
                })
                .onMessage(new OnMessageListener() {
                    @Override
                    public void onMessages(List<WeixinMessage> messages) {
                        handleMessages(messages);
                    }
                })
                .build();

        try {
            String qrCodeImgContent = client.executeLogin();
            log.info("请扫码登录");
            System.out.println("=== 微信 iLink Bot ===");
            System.out.println("请用微信扫描下方二维码登录 Bot：");
            System.out.println(qrCodeImgContent);
            System.out.println("=====================");
        } catch (Exception e) {
            log.error("启动 iLink 失败", e);
            System.out.println("✗ 微信 Bot 启动失败: " + e.getMessage());
        }
    }

    private void handleMessages(List<WeixinMessage> messages) {
        for (WeixinMessage msg : messages) {
            handleMessage(msg);
        }
    }

    private void handleMessage(WeixinMessage msg) {
        ToolExecutionContext.getAndClear();
        if (msg.getItem_list() == null) return;

        String fromUserId = msg.getFrom_user_id();

        MessageItem voiceItem = extractVoice(msg);
        if (voiceItem != null) {
            handleVoiceMessage(fromUserId, voiceItem);
            return;
        }

        MessageItem fileItem = extractFile(msg);
        if (fileItem != null) {
            handleFileMessage(fromUserId, fileItem, extractText(msg));
            return;
        }

        MessageItem imageItem = extractImage(msg);
        if (imageItem != null) {
            handleImageMessage(fromUserId, imageItem, extractText(msg));
            return;
        }

        String text = extractText(msg);
        if (text == null || text.isEmpty()) return;

        log.info("收到微信消息 from={}, text={}", fromUserId, text);

        String firstWord = text.trim().split("\\s+")[0].toLowerCase();
        if (commandRouter.hasCommand(firstWord)) {
            clearImageBuffer(fromUserId);
            String result = commandRouter.route(text, fromUserId);
            sendReply(fromUserId, result);
            return;
        }

        List<ImageEntry> images = getValidImages(fromUserId);

        if (!images.isEmpty()) {
            String context = buildImageContext(images, text);
            log.info("图片上下文拼接, from={}, 图片数={}", fromUserId, images.size());
            String result = commandRouter.route(context, fromUserId);
            if (result != null && !result.isEmpty()) {
                chatService.addHistory(fromUserId, text, result);
            }
            sendReply(fromUserId, result, images);
            return;
        }

        String result = commandRouter.route(text, fromUserId);
        sendReply(fromUserId, result);
    }

    private List<ImageEntry> getValidImages(String userId) {
        List<ImageEntry> images = imageBuffers.get(userId);
        if (images == null || images.isEmpty()) return List.of();

        images.removeIf(e -> TimeUtil.isExpired(e.timestamp, IMAGE_BUFFER_TTL));

        if (images.isEmpty()) {
            imageBuffers.remove(userId);
            log.info("图片缓存已过期, userId={}", userId);
        }
        return images;
    }

    private String buildImageContext(List<ImageEntry> images, String text) {
        StringBuilder sb = new StringBuilder();
        if (images.size() == 1) {
            sb.append("用户发送了一张图片，图片内容如下：");
            sb.append(images.get(0).desc);
        } else {
            sb.append("用户发送了").append(images.size()).append("张图片：\n");
            for (int i = 0; i < images.size(); i++) {
                sb.append("图片").append(i + 1).append("：").append(images.get(i).desc).append("\n");
            }
        }
        sb.append("\n\n用户现在说：").append(text);
        return sb.toString();
    }

    private void clearImageBuffer(String userId) {
        List<ImageEntry> removed = imageBuffers.remove(userId);
        if (removed != null && !removed.isEmpty()) {
            log.info("清除图片缓存, userId={}, 图片数={}", userId, removed.size());
        }
    }

    private void handleImageMessage(String fromUserId, MessageItem imageItem, String text) {
        log.info("收到图片消息 from={}, text={}", fromUserId, text);

        try {
            byte[] imageBytes = client.downloadImageFromMessageItem(imageItem);

            if (text != null && !text.isEmpty()) {
                String answer = imageService.recognizeImage(imageBytes, text);
                addImageToBuffer(fromUserId, imageBytes, answer);
                chatService.addHistory(fromUserId, "[图片] " + text, answer);
                sendReply(fromUserId, answer);
            } else {
                String fullDesc = imageService.recognizeImage(imageBytes,
                        "请详细描述这张图片的内容，包括所有可见的文字、数字、符号、布局等细节");
                addImageToBuffer(fromUserId, imageBytes, fullDesc);
                chatService.addHistory(fromUserId, "[用户发送了图片]", fullDesc);

                List<ImageEntry> images = imageBuffers.get(fromUserId);
                int count = images != null ? images.size() : 1;
                String shortDesc = extractFirstSentence(fullDesc);
                String msg = "【图片识别 " + count + "/" + MAX_IMAGES + "】\n\n" + shortDesc;
                if (count < MAX_IMAGES) {
                    msg += "\n\n可以继续发图片（最多" + MAX_IMAGES + "张），或发送文字提问";
                } else {
                    msg += "\n\n已达到最多" + MAX_IMAGES + "张图片，请发送文字提问或发送新图片替换";
                }
                sendReply(fromUserId, msg);
            }
        } catch (Exception e) {
            log.error("处理图片消息失败", e);
            try {
                client.sendText(fromUserId, "抱歉，图片接收失败，请稍后再试");
            } catch (Exception ex) {
                log.error("发送错误回复失败", ex);
            }
        }
    }

    private void addImageToBuffer(String userId, byte[] bytes, String desc) {
        List<ImageEntry> images = imageBuffers.computeIfAbsent(userId, k -> new ArrayList<>());
        if (images.size() >= MAX_IMAGES) {
            images.remove(0);
        }
        images.add(new ImageEntry(bytes, desc));
        log.info("添加图片到缓存, userId={}, 当前图片数={}", userId, images.size());
    }

    private void sendReply(String fromUserId, String result) {
        sendReply(fromUserId, result, null);
    }

    private void sendReply(String fromUserId, String result, List<ImageEntry> images) {
        ToolExecutionContext.PendingAction action = ToolExecutionContext.getAndClear();

        if (result == null || result.isEmpty()) {
            try {
                client.sendText(fromUserId, "抱歉，我没理解你的意思，可以再说详细一点吗？");
            } catch (Exception e) {
                log.error("发送澄清回复失败", e);
            }
            return;
        }

        try {
            if (action != null) {
                switch (action.type()) {
                    case "image_gen":
                        doImageGen(fromUserId, action.prompt());
                        return;
                    case "image_transform":
                        doImageTransform(fromUserId, action.prompt(), images);
                        return;
                    case "file_translate":
                    case "file_extract":
                    case "file_search":
                        doFileToolAction(fromUserId, action);
                        return;
                    case "file_export":
                        doFileExport(fromUserId);
                        return;
                    case "re_examine":
                        doReExamine(fromUserId, action.prompt(), images);
                        return;
                }
            }

            client.sendText(fromUserId, result);
            log.info("回复消息成功: {}", result.substring(0, Math.min(50, result.length())));

            if (action == null && voicePreferenceService.isVoiceEnabled(fromUserId)) {
                try {
                    String voiceCode = voicePreferenceService.getVoiceCode(fromUserId);
                    byte[] mp3Bytes = voiceService.textToSpeechMp3(result, voiceCode);
                    if (mp3Bytes != null) {
                        client.sendFile(fromUserId, mp3Bytes, "语音.mp3", "");
                        log.info("语音文件发送成功(MP3), size={}bytes", mp3Bytes.length);
                    }
                } catch (Exception e) {
                    log.warn("语音发送失败", e);
                }
            }
        } catch (Exception e) {
            log.error("回复失败", e);
            try {
                client.sendText(fromUserId, "抱歉，操作失败，请稍后再试");
            } catch (Exception ex) {
                log.error("发送错误回复失败", ex);
            }
        }
    }

    private void doImageGen(String fromUserId, String prompt) {
        log.info("文生图: {}", prompt);
        try {
            client.sendText(fromUserId, "正在生成中，请稍候...");
            byte[] imageBytes = imageService.generateImage(prompt);
            client.sendImage(fromUserId, imageBytes, "image.png", "");
            log.info("图片发送成功");
        } catch (Exception e) {
            log.error("图片生成失败", e);
            try { client.sendText(fromUserId, "抱歉，图片生成失败，请稍后再试"); }
            catch (Exception ex) { log.error("发送错误回复失败", ex); }
        }
    }

    private void doImageTransform(String fromUserId, String prompt, List<ImageEntry> images) {
        log.info("图生图: {}", prompt);
        try {
            client.sendText(fromUserId, "正在生成中，请稍候...");
            byte[] imageBytes;
            if (images != null && !images.isEmpty()) {
                List<byte[]> imgBytes = new ArrayList<>();
                for (ImageEntry e : images) {
                    imgBytes.add(e.bytes);
                }
                imageBytes = imageService.transformImage(imgBytes, prompt);
            } else {
                imageBytes = imageService.generateImage(prompt);
            }
            client.sendImage(fromUserId, imageBytes, "image.png", "");
            log.info("图片发送成功");
        } catch (Exception e) {
            log.error("图片变换失败", e);
            try { client.sendText(fromUserId, "抱歉，图片处理失败，请稍后再试"); }
            catch (Exception ex) { log.error("发送错误回复失败", ex); }
        }
    }

    private void doFileExport(String fromUserId) {
        String cached = lastFileResult.get(fromUserId);
        if (cached == null || cached.isEmpty()) return;
        try {
            client.sendFile(fromUserId, cached.getBytes("UTF-8"), "文件总结.txt", "");
            log.info("文件总结已发送: {}", fromUserId);
        } catch (Exception e) {
            log.error("文件总结发送失败", e);
        }
    }

    private void doReExamine(String fromUserId, String question, List<ImageEntry> images) {
        if (images == null || images.isEmpty()) return;
        ImageEntry latest = images.get(images.size() - 1);
        String prompt = "请仔细观察图片";
        if (question != null && !question.isEmpty()) {
            prompt += "，只回答这个问题：" + question;
        }
        prompt += "。直接回答，不要输出无关的图片描述。";
        try {
            client.sendText(fromUserId, "正在重新识别中...");
            String answer = imageService.recognizeImage(latest.bytes, prompt);
            latest.desc = answer;
            client.sendText(fromUserId, answer);
            log.info("重新识别图片成功");
        } catch (Exception e) {
            log.error("重新识别图片失败", e);
            try { client.sendText(fromUserId, "抱歉，重新识别失败，请稍后再试"); }
            catch (Exception ex) { log.error("发送错误回复失败", ex); }
        }
    }

    private void doFileToolAction(String fromUserId, ToolExecutionContext.PendingAction action) {
        String content = lastFileContent.get(fromUserId);
        String fileName = lastFileName.get(fromUserId);
        if (content == null || content.isEmpty()) {
            try { client.sendText(fromUserId, "没有找到已上传的文件内容，请先发送文件"); }
            catch (Exception ex) { log.error("发送错误回复失败", ex); }
            return;
        }

        String baseName = fileName != null ? fileName.replaceAll("\\.[^.]+$", "") : "file";
        String output = null;
        String outputName = null;

        switch (action.type()) {
            case "file_translate": {
                String lang = action.arg1() != null ? action.arg1() : "英语";
                String instruction = action.arg2() != null ? action.arg2() : "";
                String prompt = "请将以下文件内容翻译为" + lang + (instruction.isEmpty() ? "" : "，" + instruction) + "：\n\n" + content;
                output = llmService.chat(fromUserId, java.util.List.of(java.util.Map.of("role", "user", "content", prompt)));
                outputName = baseName + "_翻译_" + lang + ".txt";
                break;
            }
            case "file_extract": {
                String keyword = action.arg1() != null ? action.arg1() : "";
                String format = action.arg2() != null ? action.arg2() : "原文";
                String prompt = "请从以下文件内容中提取" + keyword + "，以" + format + "格式输出：\n\n" + content;
                output = llmService.chat(fromUserId, java.util.List.of(java.util.Map.of("role", "user", "content", prompt)));
                outputName = baseName + "_提取_" + keyword + ".txt";
                break;
            }
            case "file_search": {
                String query = action.arg1() != null ? action.arg1() : "";
                int contextLines = action.argInt() > 0 ? action.argInt() : 2;
                String prompt = "请在以下文件内容中搜索\"" + query + "\"，显示匹配行及前后" + contextLines + "行上下文：\n\n" + content;
                output = llmService.chat(fromUserId, java.util.List.of(java.util.Map.of("role", "user", "content", prompt)));
                outputName = baseName + "_搜索_" + query + ".txt";
                break;
            }
        }

        if (output != null && !output.isEmpty()) {
            try {
                client.sendFile(fromUserId, output.getBytes("UTF-8"), outputName, "");
                log.info("文件工具结果已发送: {}", outputName);
            } catch (Exception e) {
                log.error("发送文件失败", e);
                try { client.sendText(fromUserId, "文件处理完成但发送失败"); }
                catch (Exception ex) { log.error("发送错误回复失败", ex); }
            }
        } else {
            try { client.sendText(fromUserId, "处理失败，请稍后重试"); }
            catch (Exception ex) { log.error("发送错误回复失败", ex); }
        }
    }

    private void handleFileMessage(String fromUserId, MessageItem fileItem, String text) {
        String fileName = fileItem.getFile_item() != null
                ? fileItem.getFile_item().getFile_name() : "file";
        log.info("收到文件消息 from={}, file={}", fromUserId, fileName);
        try {
            byte[] fileBytes = client.downloadFileFromMessageItem(fileItem);
            String content = extractTextContent(fileBytes, fileName);

            if (content == null || content.isEmpty()) {
                Path tmp = FileUtil.createTempFile("wxfile_", "_" + fileName, fileBytes);
                String ossUrl = OSSUtils.upload("qwen-plus", tmp.toAbsolutePath().toString(), apiKey);
                FileUtil.deleteTempFile(tmp);
                content = "文件链接: " + ossUrl;
            }

            lastFileContent.put(fromUserId, content);
            lastFileName.put(fromUserId, fileName);

            String question = text != null && !text.isEmpty()
                    ? "用户上传了一个文件(" + fileName + ")，文件内容如下：\n\n" + content
                        + "\n\n用户说：" + text
                    : "用户上传了一个文件(" + fileName + ")，文件内容如下：\n\n" + content
                        + "\n\n请概括这个文件的内容";

            String result = commandRouter.route(question, fromUserId);
            sendReply(fromUserId, result);
            lastFileResult.put(fromUserId, result);
        } catch (Exception e) {
            log.error("处理文件消息失败", e);
            try { client.sendText(fromUserId, "抱歉，文件处理失败，请稍后再试"); }
            catch (Exception ex) { log.error("发送错误回复失败", ex); }
        }
    }

    private void handleFileToolResult(String fromUserId, String result) {
        try {
            String content = lastFileContent.get(fromUserId);
            String fileName = lastFileName.get(fromUserId);
            if (content == null || content.isEmpty()) {
                client.sendText(fromUserId, "没有找到已上传的文件内容，请先发送文件");
                return;
            }

            String baseName = fileName != null ? fileName.replaceAll("\\.[^.]+$", "") : "file";
            String jsonArgs = result.substring(result.indexOf(':') + 1);
            com.fasterxml.jackson.databind.JsonNode args = new com.fasterxml.jackson.databind.ObjectMapper().readTree(jsonArgs);

            String output = null;
            String outputName = null;

            if (result.startsWith("FILE_TRANSLATE:")) {
                String lang = args.has("target_language") ? args.get("target_language").asText() : "英语";
                String instruction = args.has("instruction") ? args.get("instruction").asText() : "";
                String prompt = "请将以下文件内容翻译为" + lang + (instruction.isEmpty() ? "" : "，" + instruction) + "：\n\n" + content;
                output = llmService.chat(fromUserId, List.of(Map.of("role", "user", "content", prompt)));
                outputName = baseName + "_翻译_" + lang + ".txt";
            } else if (result.startsWith("FILE_EXTRACT:")) {
                String keyword = args.has("keyword") ? args.get("keyword").asText() : "";
                String format = args.has("format") ? args.get("format").asText() : "原文";
                String prompt = "请从以下文件内容中提取" + keyword + "，以" + format + "格式输出：\n\n" + content;
                output = llmService.chat(fromUserId, List.of(Map.of("role", "user", "content", prompt)));
                outputName = baseName + "_提取_" + keyword + ".txt";
            } else if (result.startsWith("FILE_SEARCH:")) {
                String query = args.has("query") ? args.get("query").asText() : "";
                int contextLines = args.has("context_lines") ? args.get("context_lines").asInt() : 2;
                String prompt = "请在以下文件内容中搜索\"" + query + "\"，显示匹配行及前后" + contextLines + "行上下文：\n\n" + content;
                output = llmService.chat(fromUserId, List.of(Map.of("role", "user", "content", prompt)));
                outputName = baseName + "_搜索_" + query + ".txt";
            }

            if (output != null && !output.isEmpty()) {
                client.sendFile(fromUserId, output.getBytes("UTF-8"), outputName, "");
                log.info("文件工具结果已发送: {}", outputName);
            } else {
                client.sendText(fromUserId, "处理失败，请稍后重试");
            }
        } catch (Exception e) {
            log.error("处理文件工具结果失败", e);
            try { client.sendText(fromUserId, "文件处理失败，请稍后重试"); }
            catch (Exception ex) { log.error("发送错误回复失败", ex); }
        }
    }

    private String extractTextContent(byte[] bytes, String fileName) {
        String lower = fileName.toLowerCase();

        boolean isOffice = FileUtil.isOfficeExtension(lower);
        if (!isOffice && FileUtil.isBinarySignature(bytes)) return null;

        boolean isText = FileUtil.isTextExtension(lower);

        if (isText) return FileUtil.decodeText(bytes, 50000);
        if (isOffice) return extractWithPoi(bytes, fileName);

        return null;
    }

    private String extractWithPoi(byte[] bytes, String fileName) {
        try {
            String lower = fileName.toLowerCase();
            if (lower.endsWith(".pdf")) {
                var doc = Loader.loadPDF(bytes);
                var stripper = new PDFTextStripper();
                String text = stripper.getText(doc);
                doc.close();
                return text.length() > 50000 ? text.substring(0, 50000) + "\n...(内容过长已截断)" : text;
            }
            if (lower.endsWith(".docx")) {
                XWPFDocument doc = new XWPFDocument(new java.io.ByteArrayInputStream(bytes));
                XWPFWordExtractor ex = new XWPFWordExtractor(doc);
                String text = ex.getText();
                ex.close();
                return text.length() > 50000 ? text.substring(0, 50000) + "\n...(内容过长已截断)" : text;
            }
            if (lower.endsWith(".xlsx")) {
                XSSFWorkbook wb = new XSSFWorkbook(new java.io.ByteArrayInputStream(bytes));
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                    sb.append("Sheet: ").append(wb.getSheetName(i)).append("\n");
                    var sheet = wb.getSheetAt(i);
                    for (int r = 0; r <= sheet.getLastRowNum(); r++) {
                        var row = sheet.getRow(r);
                        if (row != null) {
                            for (var cell : row) {
                                String v = cell.toString().trim();
                                if (!v.isEmpty()) sb.append(v).append("\t");
                            }
                            sb.append("\n");
                        }
                    }
                }
                wb.close();
                String text = sb.toString();
                return text.length() > 50000 ? text.substring(0, 50000) + "\n...(内容过长已截断)" : text;
            }
        } catch (Exception e) {
            log.warn("POI 提取失败, 回退文本尝试: {}", e.getMessage());
        }
        return FileUtil.decodeText(bytes, 5000);
    }

    private MessageItem extractFile(WeixinMessage msg) {
        if (msg.getItem_list() == null) return null;
        for (MessageItem item : msg.getItem_list()) {
            if (item.getFile_item() != null) return item;
        }
        return null;
    }

    private void handleVoiceMessage(String fromUserId, MessageItem voiceItem) {
        log.info("收到语音消息 from={}", fromUserId);
        String transcript = voiceItem.getVoice_item().getText();
        if (transcript == null || transcript.isEmpty()) {
            transcript = "[语音消息，未能识别文字]";
        }
        log.info("语音转文字: {}", transcript);

        String result = commandRouter.route(transcript, fromUserId);
        if (result != null && !result.isEmpty()) {
            chatService.addHistory(fromUserId, transcript, result);
        }
        sendReply(fromUserId, result);
    }

    private MessageItem extractVoice(WeixinMessage msg) {
        if (msg.getItem_list() == null) return null;
        for (MessageItem item : msg.getItem_list()) {
            if (item.getVoice_item() != null) {
                return item;
            }
        }
        return null;
    }

    private MessageItem extractImage(WeixinMessage msg) {
        if (msg.getItem_list() == null) return null;
        for (MessageItem item : msg.getItem_list()) {
            if (item.getImage_item() != null) {
                return item;
            }
        }
        return null;
    }

    private String extractText(WeixinMessage msg) {
        if (msg.getItem_list() == null) return null;
        for (MessageItem item : msg.getItem_list()) {
            if (item.getText_item() != null) {
                return item.getText_item().getText();
            }
        }
        return null;
    }

    private String extractFirstSentence(String text) {
        if (text == null || text.isEmpty()) return "";
        int period = text.indexOf("。");
        if (period > 0 && period < 200) {
            return text.substring(0, period + 1);
        }
        int newline = text.indexOf("\n");
        if (newline > 0 && newline < 200) {
            return text.substring(0, newline);
        }
        return text.length() > 200 ? text.substring(0, 200) + "..." : text;
    }

    @PreDestroy
    public void cleanup() {
        if (client != null) {
            client.close();
            log.info("iLink 客户端已关闭");
        }
    }
}
