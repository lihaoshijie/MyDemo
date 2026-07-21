package com.Myself.demo.bot;

import com.Myself.demo.service.ImageService;
import com.Myself.demo.service.VoiceService;
import com.Myself.demo.service.VoiceType;
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
import java.util.concurrent.ConcurrentHashMap;
import java.nio.file.Files;
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

    @Value("${llm.api-key}")
    private String apiKey;

    private ILinkClient client;
    private static final long IMAGE_BUFFER_TTL = 5 * 60 * 1000;
    private static final int MAX_IMAGES = 5;

    private final ConcurrentHashMap<String, List<ImageEntry>> imageBuffers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> voiceMode = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> voicePref = new ConcurrentHashMap<>();

    private static class ImageEntry {
        byte[] bytes;
        String desc;
        long timestamp;

        ImageEntry(byte[] bytes, String desc) {
            this.bytes = bytes;
            this.desc = desc;
            this.timestamp = System.currentTimeMillis();
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
            long deadline = System.currentTimeMillis() + 3000;
            while (System.currentTimeMillis() < deadline) {
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

        if ("开启语音".equals(text) || "打开语音".equals(text) || "语音模式".equals(text)) {
            voiceMode.put(fromUserId, true);
            sendReply(fromUserId, "语音模式已开启，我的回复会附带语音播报");
            return;
        }
        if ("关闭语音".equals(text) || "关掉语音".equals(text) || "结束语音".equals(text)) {
            voiceMode.remove(fromUserId);
            voicePref.remove(fromUserId);
            sendReply(fromUserId, "语音模式已关闭");
            return;
        }

        if ("音色列表".equals(text) || "有哪些音色".equals(text) || "音色".equals(text)) {
            StringBuilder sb = new StringBuilder("🎙️ 可用音色：\n\n");
            for (VoiceType vt : VoiceType.values()) {
                sb.append(vt.getDescription()).append("\n");
            }
            sb.append("\n切换方式：切换 + 音色名，如「切换童声」");
            sendReply(fromUserId, sb.toString());
            return;
        }

        String[] switchPrefixes = {"切换", "换成", "换", "用"};
        for (String p : switchPrefixes) {
            if (text.startsWith(p)) {
                String name = text.substring(p.length()).trim();
                if (!name.isEmpty()) {
                    VoiceType vt = VoiceType.fromName(name);
                    voicePref.put(fromUserId, vt.getCode());
                    sendReply(fromUserId, "已切换音色为 " + vt.getDescription());
                    return;
                }
            }
        }

        String firstWord = text.trim().split("\\s+")[0].toLowerCase();
        if (commandRouter.hasCommand(firstWord)) {
            clearImageBuffer(fromUserId);
            String result = commandRouter.route(text, fromUserId);
            sendReply(fromUserId, result);
            return;
        }

        List<ImageEntry> images = getValidImages(fromUserId);

        if (!images.isEmpty()) {
            if (isRefreshRequest(text)) {
                log.info("重新识别图片, from={}", fromUserId);
                String lastQuestion = chatService.getLastUserQuestion(fromUserId);
                String reExamPrompt = buildReExamPrompt(text, lastQuestion);
                ImageEntry latest = images.get(images.size() - 1);
                String answer = imageService.recognizeImage(latest.bytes, reExamPrompt);
                latest.desc = answer;
                chatService.addHistory(fromUserId, "[重新识别图片]", answer);
                sendReply(fromUserId, answer);
                return;
            }

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

        long now = System.currentTimeMillis();
        images.removeIf(e -> now - e.timestamp >= IMAGE_BUFFER_TTL);

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
        if (result == null || result.isEmpty()) {
            try {
                client.sendText(fromUserId, "抱歉，我没理解你的意思，可以再说详细一点吗？");
            } catch (Exception e) {
                log.error("发送澄清回复失败", e);
            }
            return;
        }

        try {
            if (result.startsWith("IMG_GEN:") || result.startsWith("TRANSFORM_GEN:")) {
                String prompt = result.startsWith("TRANSFORM_GEN:")
                        ? result.substring(14)
                        : result.substring(8);
                boolean isTransform = result.startsWith("TRANSFORM_GEN:");
                log.info("生成图片: mode={}, prompt={}", isTransform ? "图生图" : "文生图", prompt);
                client.sendText(fromUserId, "正在生成中，请稍候...");
                byte[] imageBytes;
                if (isTransform && images != null && !images.isEmpty()) {
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
            } else {
                client.sendText(fromUserId, result);
                log.info("回复消息成功: {}", result.substring(0, Math.min(50, result.length())));
            }

            if (Boolean.TRUE.equals(voiceMode.get(fromUserId))
                    && !result.startsWith("IMG_GEN:") && !result.startsWith("TRANSFORM_GEN:")) {
                try {
                    String voiceCode = voicePref.get(fromUserId);
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

    private void handleFileMessage(String fromUserId, MessageItem fileItem, String text) {
        String fileName = fileItem.getFile_item() != null
                ? fileItem.getFile_item().getFile_name() : "file";
        log.info("收到文件消息 from={}, file={}", fromUserId, fileName);
        try {
            byte[] fileBytes = client.downloadFileFromMessageItem(fileItem);
            String content = extractTextContent(fileBytes, fileName);

            if (content == null || content.isEmpty()) {
                Path tmp = Files.createTempFile("wxfile_", "_" + fileName);
                Files.write(tmp, fileBytes);
                String ossUrl = OSSUtils.upload("qwen-plus", tmp.toAbsolutePath().toString(), apiKey);
                Files.deleteIfExists(tmp);
                content = "文件链接: " + ossUrl;
            }

            String question = text != null && !text.isEmpty()
                    ? "用户上传了一个文件(" + fileName + ")，文件内容如下：\n\n" + content
                        + "\n\n用户说：" + text
                    : "用户上传了一个文件(" + fileName + ")，文件内容如下：\n\n" + content
                        + "\n\n请概括这个文件的内容";

            String result = commandRouter.route(question, fromUserId);
            sendReply(fromUserId, result);

            boolean wantFile = text != null && (text.contains("总结") || text.contains("导出")
                    || text.contains("写文件") || text.contains("生成文件"));
            if (wantFile && result != null && !result.isEmpty()) {
                String baseName = fileName.replaceAll("\\.[^.]+$", "");
                client.sendFile(fromUserId, result.getBytes("UTF-8"), baseName + "_总结.txt", "");
                log.info("总结文件已发送: {}", baseName);
            }
        } catch (Exception e) {
            log.error("处理文件消息失败", e);
            try { client.sendText(fromUserId, "抱歉，文件处理失败，请稍后再试"); }
            catch (Exception ex) { log.error("发送错误回复失败", ex); }
        }
    }

    private String extractTextContent(byte[] bytes, String fileName) {
        String lower = fileName.toLowerCase();

        boolean isOffice = lower.endsWith(".doc") || lower.endsWith(".docx")
                || lower.endsWith(".xls") || lower.endsWith(".xlsx")
                || lower.endsWith(".ppt") || lower.endsWith(".pptx");

        if (!isOffice && isBinarySignature(bytes)) return null;

        boolean isText = lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".json")
                || lower.endsWith(".csv") || lower.endsWith(".xml") || lower.endsWith(".html")
                || lower.endsWith(".log") || lower.endsWith(".java") || lower.endsWith(".py")
                || lower.endsWith(".yaml") || lower.endsWith(".yml") || lower.endsWith(".sql")
                || lower.endsWith(".c") || lower.endsWith(".cpp") || lower.endsWith(".h")
                || lower.endsWith(".js") || lower.endsWith(".ts") || lower.endsWith(".css")
                || lower.endsWith(".sh") || lower.endsWith(".bat") || lower.endsWith(".ini")
                || lower.endsWith(".cfg") || lower.endsWith(".conf");

        if (isText) return tryDecodeText(bytes, 50000);
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
        return tryDecodeText(bytes, 5000);
    }

    private String tryDecodeText(byte[] bytes, int maxLen) {
        for (String charset : new String[]{"UTF-8", "GBK"}) {
            try {
                String s = new String(bytes, charset);
                int len = Math.min(s.length(), 1000);
                int valid = 0;
                for (int i = 0; i < len; i++) {
                    char c = s.charAt(i);
                    if (c >= 0x4e00 && c <= 0x9fff) valid++;
                    else if (c >= 'a' && c <= 'z') valid++;
                    else if (c >= 'A' && c <= 'Z') valid++;
                    else if (c >= '0' && c <= '9') valid++;
                    else if (c == ' ' || c == '\n' || c == '\r' || c == '\t') valid++;
                }
                if (valid > len * 0.5) {
                    return s.length() > maxLen ? s.substring(0, maxLen) + "\n...(内容过长已截断)" : s;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private boolean isBinarySignature(byte[] bytes) {
        if (bytes == null || bytes.length < 4) return false;
        int b0 = bytes[0] & 0xFF, b1 = bytes[1] & 0xFF, b2 = bytes[2] & 0xFF, b3 = bytes[3] & 0xFF;
        return (b0 == 0x50 && b1 == 0x4B)  // PK (zip/docx/xlsx/pptx)
            || (b0 == 0x25 && b1 == 0x50 && b2 == 0x44 && b3 == 0x46)  // %PDF
            || (b0 == 0xD0 && b1 == 0xCF && b2 == 0x11 && b3 == 0xE0)  // .doc
            || (b0 == 0x89 && b1 == 0x50 && b2 == 0x4E && b3 == 0x47)  // PNG
            || (b0 == 0xFF && b1 == 0xD8)  // JPEG
            || (b0 == 0x47 && b1 == 0x49 && b2 == 0x46)  // GIF
            || (b0 == 0x1F && b1 == 0x8B);  // GZIP
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

    private boolean isRefreshRequest(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase();
        String[] keywords = {"仔细看看", "再看看", "重新看看", "再看一下", "仔细观察",
                "重新识别", "重新描述", "再描述", "仔细描述", "重新读", "再读"};
        for (String kw : keywords) {
            if (lower.contains(kw)) return true;
        }
        return false;
    }

    private boolean isTransformRequest(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase();
        String[] keywords = {"变", "改成", "变成", "转成", "换", "调成", "调整",
                "风格", "卡通", "动漫", "油画", "素描", "水彩", "手绘",
                "滤镜", "复古", "黑白", "像素", "抽象", "写实",
                "P图", "p图", "p掉", "P掉", "编辑", "修改", "美化",
                "移除", "去掉", "删除", "抠掉", "擦除", "消除",
                "换成", "替换", "加", "加上", "添加"};
        for (String kw : keywords) {
            if (lower.contains(kw)) return true;
        }
        return false;
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

    private String buildReExamPrompt(String userText, String lastQuestion) {
        String lower = userText.toLowerCase();
        String[] correctionKeywords = {"不是", "不对", "错了", "有误", "错误", "搞错", "错了吧"};
        boolean isCorrection = false;
        for (String kw : correctionKeywords) {
            if (lower.contains(kw)) {
                isCorrection = true;
                break;
            }
        }

        String coreQuestion = userText
                .replaceAll("你再?仔细看看", "")
                .replaceAll("你再?看看", "")
                .replaceAll("重新看看", "")
                .replaceAll("仔细观察", "")
                .trim();

        if (isCorrection) {
            return "用户指出之前的回答有误。用户说：'" + userText + "'。"
                    + "请仔细重新观察图片，准确判断用户质疑的内容是否正确，"
                    + "直接回答是或不是，并给出依据。不要输出无关描述。";
        }

        if (!coreQuestion.isEmpty()) {
            return "请仔细观察图片，只回答这个问题：" + coreQuestion
                    + "。直接回答，不要输出无关的图片描述。";
        }

        if (lastQuestion != null && !lastQuestion.isEmpty()
                && !lastQuestion.startsWith("[")
                && lastQuestion.length() < 50) {
            return "请仔细观察图片，只回答这个问题：" + lastQuestion
                    + "。直接回答，不要输出无关的图片描述。";
        }

        return "请仔细重新观察这张图片的细节，给出更准确的描述。";
    }

    @PreDestroy
    public void cleanup() {
        if (client != null) {
            client.close();
            log.info("iLink 客户端已关闭");
        }
    }
}
