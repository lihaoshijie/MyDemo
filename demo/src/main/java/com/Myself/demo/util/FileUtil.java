package com.Myself.demo.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public class FileUtil {

    private static final Logger log = LoggerFactory.getLogger(FileUtil.class);
    /**
     * 存入常见的文本拓展名
     */
    private static final List<String> TEXT_EXTENSIONS = Arrays.asList(
            ".txt", ".md", ".json", ".csv", ".xml", ".html", ".log",
            ".java", ".py", ".yaml", ".yml", ".sql",
            ".c", ".cpp", ".h", ".js", ".ts", ".css",
            ".sh", ".bat", ".ini", ".cfg", ".conf");

    private static final List<String> OFFICE_EXTENSIONS = Arrays.asList(
            ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx", ".pdf");

    private FileUtil() {}

    /**
     *创建临时文件+写入数据
     */
    public static Path createTempFile(String prefix, String suffix, byte[] data) throws IOException {
        Path temp = Files.createTempFile(prefix, suffix);
        Files.write(temp, data);
        return temp;
    }

    /**
     * 删除单个临时文件
     * @param path
     */

    public static void deleteTempFile(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("删除临时文件失败: {}", path, e);
        }
    }

    /**
     * 遍历删除
     * @param paths
     */
    public static void deleteTempFiles(List<Path> paths) {
        for (Path p : paths) {
            try {
                Files.deleteIfExists(p);
            } catch (IOException e) {
                log.warn("删除临时文件失败: {}", p, e);
            }
        }
    }

    /**
     * 拓展名判断
     * @param fileName
     * @return
     */

    public static boolean isTextExtension(String fileName) {
        String lower = fileName.toLowerCase();
        return TEXT_EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    public static boolean isOfficeExtension(String fileName) {
        String lower = fileName.toLowerCase();
        return OFFICE_EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    public static boolean isKnownExtension(String fileName) {
        return isTextExtension(fileName) || isOfficeExtension(fileName);
    }

    /**
     * 二进制魔数检测
     * @param bytes
     * @return
     */
    public static boolean isBinarySignature(byte[] bytes) {
        if (bytes == null || bytes.length < 4) return false;
        int b0 = bytes[0] & 0xFF, b1 = bytes[1] & 0xFF, b2 = bytes[2] & 0xFF, b3 = bytes[3] & 0xFF;
        return (b0 == 0x50 && b1 == 0x4B)
                || (b0 == 0x25 && b1 == 0x50 && b2 == 0x44 && b3 == 0x46)
                || (b0 == 0xD0 && b1 == 0xCF && b2 == 0x11 && b3 == 0xE0)
                || (b0 == 0x89 && b1 == 0x50 && b2 == 0x4E && b3 == 0x47)
                || (b0 == 0xFF && b1 == 0xD8)
                || (b0 == 0x47 && b1 == 0x49 && b2 == 0x46)
                || (b0 == 0x1F && b1 == 0x8B);
    }

    public static String detectEncoding(byte[] bytes) {
        return detectEncoding(bytes, null);
    }

    /**
     * 编码检测
     * @param bytes
     * @param preferred
     * @return
     */
    public static String detectEncoding(byte[] bytes, String preferred) {
        if (preferred != null) {
            try {
                new String(bytes, preferred);
                return preferred;
            } catch (Exception ignored) {}
        }
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
                if (valid > len * 0.5) return charset;
            } catch (Exception ignored) {}
        }
        return null;
    }

    public static String decodeText(byte[] bytes) {
        return decodeText(bytes, -1);
    }

    /**
     * 文本解码
     * @param bytes
     * @param maxLen
     * @return
     */

    public static String decodeText(byte[] bytes, int maxLen) {
        String charset = detectEncoding(bytes);
        if (charset == null) return null;
        try {
            String s = new String(bytes, charset);
            if (maxLen > 0 && s.length() > maxLen) {
                return s.substring(0, maxLen) + "\n...(内容过长已截断)";
            }
            return s;
        } catch (Exception e) {
            return null;
        }
    }
}
