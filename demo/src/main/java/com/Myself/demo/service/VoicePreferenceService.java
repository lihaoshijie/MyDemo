package com.Myself.demo.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

@Service
public class VoicePreferenceService {

    private final ConcurrentHashMap<String, String> voicePref = new ConcurrentHashMap<>();

    public String getVoiceCode(String userId) {
        return voicePref.get(userId);
    }

    public void setVoiceCode(String userId, String code) {
        voicePref.put(userId, code);
    }

    public void remove(String userId) {
        voicePref.remove(userId);
    }
}
