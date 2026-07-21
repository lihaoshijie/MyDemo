package com.Myself.demo.service;

public enum VoiceType {
    MALE("longanyang", "默认男声"),
    FEMALE_V3("longanhuan_v3", "女声元气"),
    FEMALE("longanhuan", "女声欢脱"),
    FEMALE_CHILD("longhuhu_v3", "女声童声"),
    FEMALE_COOL("longyingjing_v3", "女声冷静"),
    FEMALE_WARM("longyingling_v3", "女声共情"),
    FEMALE_CHEERFUL("longxiaochun_v3", "女声知性");

    private final String code;
    private final String description;

    VoiceType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() { return code; }
    public String getDescription() { return description; }

    public static VoiceType fromName(String name) {
        if (name == null || name.trim().isEmpty()) return MALE;
        for (VoiceType vt : values()) {
            if (vt.description.contains(name) || vt.name().equalsIgnoreCase(name)) {
                return vt;
            }
        }
        return MALE;
    }
}
