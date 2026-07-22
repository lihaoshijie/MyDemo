package com.Myself.demo.util;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class TimeUtil {

    private static final DateTimeFormatter DEFAULT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter COMPACT_FORMAT = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    public static long nowMillis() {
        return System.currentTimeMillis();
    }

    public static long afterMillis(long ms) {
        return System.currentTimeMillis() + ms;
    }

    public static boolean isPast(long deadline) {
        return System.currentTimeMillis() >= deadline;
    }

    public static long remainMs(long deadline) {
        return Math.max(0, deadline - System.currentTimeMillis());
    }

    public static boolean isExpired(long timestamp, long ttlMs) {
        return System.currentTimeMillis() - timestamp >= ttlMs;
    }

    public static String formatDuration(long ms) {
        if (ms < 1000) return ms + "毫秒";
        long seconds = ms / 1000;
        if (seconds < 60) return seconds + "秒";
        long minutes = seconds / 60;
        long secs = seconds % 60;
        if (minutes < 60) return secs > 0 ? minutes + "分" + secs + "秒" : minutes + "分钟";
        long hours = minutes / 60;
        long mins = minutes % 60;
        return mins > 0 ? hours + "小时" + mins + "分" : hours + "小时";
    }

    public static String formatMillis(long ms) {
        return formatDuration(ms);
    }

    public static String formatTime(long timestamp) {
        LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
        return dateTime.format(DEFAULT_FORMAT);
    }

    public static String formatTimeCompact(long timestamp) {
        LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
        return dateTime.format(COMPACT_FORMAT);
    }

    public static String formatTime(LocalDateTime dateTime) {
        return dateTime.format(DEFAULT_FORMAT);
    }

    public static long seconds(long seconds) {
        return seconds * 1000;
    }

    public static long minutes(long minutes) {
        return minutes * 60 * 1000;
    }

    public static long hours(long hours) {
        return hours * 60 * 60 * 1000;
    }

    public static Duration toDuration(long ms) {
        return Duration.ofMillis(ms);
    }
}
