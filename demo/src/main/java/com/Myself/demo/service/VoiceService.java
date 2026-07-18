package com.Myself.demo.service;

import com.alibaba.dashscope.audio.http_tts.HttpSpeechSynthesisParam;
import com.alibaba.dashscope.audio.http_tts.HttpSpeechSynthesizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;

@Slf4j
@Service
public class VoiceService {

    private final String apiKey;

    public VoiceService(@Value("${llm.api-key}") String apiKey) {
        this.apiKey = apiKey;
        log.info("VoiceService 初始化完成");
    }

    public byte[] textToSpeech(String text) {
        try {
            HttpSpeechSynthesizer synthesizer = new HttpSpeechSynthesizer();
            HttpSpeechSynthesisParam param = HttpSpeechSynthesisParam.builder()
                    .apiKey(apiKey)
                    .model("cosyvoice-v3-flash")
                    .voice("longanyang")
                    .text(text)
                    .format("wav")
                    .sampleRate(24000)
                    .build();

            ByteBuffer audioData = synthesizer.callAndReturnAudio(param);
            if (audioData != null && audioData.hasRemaining()) {
                byte[] bytes = new byte[audioData.remaining()];
                audioData.get(bytes);
                log.info("语音合成成功, text={}, size={}bytes",
                        text.substring(0, Math.min(30, text.length())), bytes.length);
                return bytes;
            }
            log.warn("语音合成返回空数据");
            return null;
        } catch (Exception e) {
            log.error("语音合成失败", e);
            return null;
        }
    }
}
