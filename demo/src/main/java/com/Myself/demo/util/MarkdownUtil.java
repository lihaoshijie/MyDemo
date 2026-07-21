package com.Myself.demo.util;

public class MarkdownUtil {

    public static String toPlainText(String md) {
        if (md == null || md.isEmpty()) return "";
        return md.replaceAll("###{1,5}\\s*", "")
                .replaceAll("\\*\\*([^*]+)\\*\\*", "$1")
                .replaceAll("\\*([^*]+)\\*", "$1")
                .replaceAll("__([^_]+)__", "$1")
                .replaceAll("_([^_]+)_", "$1")
                .replaceAll("~~([^~]+)~~", "$1")
                .replaceAll("`{1,3}[^`]*`{1,3}", "")
                .replaceAll("\\[([^]]+)]\\([^)]+\\)", "$1")
                .replaceAll("!\\[([^]]*)]\\([^)]+\\)", "$1")
                .replaceAll("^[-*+]\\s+", "、")
                .replaceAll("^\\d+\\.\\s+", "")
                .replaceAll("^>\\s+", "")
                .replaceAll("\\|[-:| ]+\\|", "")
                .replaceAll("---+", "")
                .replaceAll("[\n\r]{2,}", "，")
                .replaceAll("[\n\r]", "，")
                .replaceAll("，+", "，")
                .replaceAll("^，", "")
                .replaceAll("，$", "")
                .trim();
    }

    public static String toShortSummary(String md, int maxLen) {
        String plain = toPlainText(md);
        return plain.length() > maxLen ? plain.substring(0, maxLen) + "..." : plain;
    }
}
