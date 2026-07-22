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
        String n = name.replace("女生", "女声").replace("男生", "男声");
        System.out.println("[VoiceTypeDebug] input=" + name + ", normalized=" + n);
        for (VoiceType vt : values()) {
            System.out.println("[VoiceTypeDebug] checking " + vt.name() + " desc=\"" + vt.description + "\" contains=" + vt.description.contains(n) + " nameEq=" + vt.name().equalsIgnoreCase(n));
            if (vt.description.contains(n) || vt.name().equalsIgnoreCase(n)) {
                System.out.println("[VoiceTypeDebug] matched: " + vt.name());
                return vt;
            }
        }
        System.out.println("[VoiceTypeDebug] no match, returning MALE");
        return MALE;
    }
}
