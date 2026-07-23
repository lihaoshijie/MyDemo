package com.Myself.demo.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

@Service
public class VoicePreferenceService {

    private final ConcurrentHashMap<String, String> voicePref = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> voiceEnabled = new ConcurrentHashMap<>();

    public String getVoiceCode(String userId) {
        return voicePref.get(userId);
    }

    public void setVoiceCode(String userId, String code) {
        voicePref.put(userId, code);
    }

    public void remove(String userId) {
        voicePref.remove(userId);
        voiceEnabled.remove(userId);
    }

    public boolean isVoiceEnabled(String userId) {
        return voiceEnabled.getOrDefault(userId, false);
    }

    public void enableVoice(String userId) {
        voiceEnabled.put(userId, true);
    }

    public void disableVoice(String userId) {
        voiceEnabled.put(userId, false);
    }
}
