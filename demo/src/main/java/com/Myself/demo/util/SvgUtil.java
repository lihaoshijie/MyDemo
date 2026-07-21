package com.Myself.demo.util;

import java.io.ByteArrayOutputStream;
import java.util.regex.Pattern;

public class SvgUtil {

    private static final Pattern SVG_TAG = Pattern.compile("<svg[^>]*>");

    public static boolean isValid(String svg) {
        if (svg == null || svg.trim().isEmpty()) return false;
        String lower = svg.toLowerCase();
        return lower.contains("<svg") && lower.contains("</svg>")
                && !lower.contains("<script") && !lower.contains("onclick");
    }

    public static String extract(String text) {
        if (text == null) return null;
        String lower = text.toLowerCase();
        int start = lower.indexOf("<svg");
        if (start < 0) return null;
        int end = lower.indexOf("</svg>");
        if (end < 0) return null;
        return text.substring(start, end + 6);
    }

    public static byte[] toPngBytes(String svg) {
        if (!isValid(svg)) return null;
        String wrapped = "<svg xmlns=\"http://www.w3.org/2000/svg\" " + svg.substring(5);
        try {
            var transcoder = new org.apache.batik.transcoder.image.PNGTranscoder();
            transcoder.addTranscodingHint(
                    org.apache.batik.transcoder.image.PNGTranscoder.KEY_WIDTH, 800f);
            var input = new org.apache.batik.transcoder.TranscoderInput(
                    new java.io.StringReader(wrapped));
            var output = new org.apache.batik.transcoder.TranscoderOutput(
                    new ByteArrayOutputStream());
            transcoder.transcode(input, output);
            return ((ByteArrayOutputStream) output.getOutputStream()).toByteArray();
        } catch (Exception e) {
            return null;
        }
    }
}
