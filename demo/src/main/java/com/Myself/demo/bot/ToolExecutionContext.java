package com.Myself.demo.bot;

public class ToolExecutionContext {

    private static final ThreadLocal<PendingAction> pending = new ThreadLocal<>();

    public static void recordImageGen(String prompt) {
        pending.set(new PendingAction("image_gen", prompt, false, null, null, 0));
    }

    public static void recordImageTransform(String prompt) {
        pending.set(new PendingAction("image_transform", prompt, true, null, null, 0));
    }

    public static void recordFileTranslate(String targetLanguage, String instruction) {
        pending.set(new PendingAction("file_translate", null, false, targetLanguage, instruction, 0));
    }

    public static void recordFileExtract(String keyword, String format) {
        pending.set(new PendingAction("file_extract", null, false, keyword, format, 0));
    }

    public static void recordFileSearch(String query, int contextLines) {
        pending.set(new PendingAction("file_search", null, false, query, null, contextLines));
    }

    public static void recordFileExport() {
        pending.set(new PendingAction("file_export", null, false, null, null, 0));
    }

    public static void recordReExamine(String question) {
        pending.set(new PendingAction("re_examine", question, false, null, null, 0));
    }

    public static PendingAction getAndClear() {
        PendingAction result = pending.get();
        pending.remove();
        return result;
    }

    public record PendingAction(
            String type,
            String prompt,
            boolean isTransform,
            String arg1,
            String arg2,
            int argInt) {

        public boolean isImageAction() {
            return "image_gen".equals(type) || "image_transform".equals(type);
        }

        public boolean isFileAction() {
            return type != null && type.startsWith("file_");
        }
    }
}
