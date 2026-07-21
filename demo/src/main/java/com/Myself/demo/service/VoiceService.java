package com.Myself.demo.service;

import com.alibaba.dashscope.audio.http_tts.HttpSpeechSynthesisParam;
import com.alibaba.dashscope.audio.http_tts.HttpSpeechSynthesizer;
import com.alibaba.dashscope.utils.Constants;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;

@Slf4j
@Service
public class VoiceService {

    @Value("${llm.api-key}")
    private String apiKey;

    @PostConstruct
    public void init() {
        Constants.apiKey = apiKey;
        log.info("VoiceService 初始化完成");
    }

    public byte[] textToSpeechMp3(String text, String voiceName) {
        try {
            HttpSpeechSynthesizer synthesizer = new HttpSpeechSynthesizer();
            HttpSpeechSynthesisParam param = HttpSpeechSynthesisParam.builder()
                    .model("cosyvoice-v3-flash")
                    .voice(voiceName != null ? voiceName : "longanyang")
                    .text(text)
                    .format("mp3")
                    .sampleRate(24000)
                    .build();

            ByteBuffer audioData = synthesizer.callAndReturnAudio(param);
            if (audioData != null && audioData.hasRemaining()) {
                byte[] bytes = new byte[audioData.remaining()];
                audioData.get(bytes);
                log.info("MP3 合成成功, voice={}, size={}bytes", voiceName, bytes.length);
                return bytes;
            }
            log.warn("语音合成返回空数据");
            return null;
        } catch (Exception e) {
            log.error("语音合成失败, voice={}", voiceName, e);
            return null;
        }
    }

    public byte[] textToSpeechMp3(String text) {
        return textToSpeechMp3(text, null);
    }
}
