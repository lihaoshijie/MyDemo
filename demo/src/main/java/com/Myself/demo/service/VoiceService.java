package com.Myself.demo.service;

import com.alibaba.dashscope.audio.http_tts.HttpSpeechSynthesisParam;
import com.alibaba.dashscope.audio.http_tts.HttpSpeechSynthesizer;
import com.alibaba.dashscope.utils.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Service
public class VoiceService {

    private static final String FFMPEG = "D:\\FFmpeg-8.1.2\\bin\\ffmpeg.exe";

    public VoiceService(@Value("${llm.api-key}") String apiKey) {
        Constants.apiKey = apiKey;
        log.info("VoiceService 初始化完成");
    }

    public byte[] textToSpeechMp3(String text) {
        try {
            HttpSpeechSynthesizer synthesizer = new HttpSpeechSynthesizer();
            HttpSpeechSynthesisParam param = HttpSpeechSynthesisParam.builder()
                    .model("cosyvoice-v3-flash")
                    .voice("longanyang")
                    .text(text)
                    .format("mp3")
                    .sampleRate(24000)
                    .build();

            ByteBuffer audioData = synthesizer.callAndReturnAudio(param);
            if (audioData != null && audioData.hasRemaining()) {
                byte[] bytes = new byte[audioData.remaining()];
                audioData.get(bytes);
                log.info("MP3 语音合成成功, size={}bytes", bytes.length);
                return bytes;
            }
            log.warn("语音合成返回空数据");
            return null;
        } catch (Exception e) {
            log.error("语音合成失败", e);
            return null;
        }
    }

    public byte[] textToSpeechWav(String text) {
        try {
            HttpSpeechSynthesizer synthesizer = new HttpSpeechSynthesizer();
            HttpSpeechSynthesisParam param = HttpSpeechSynthesisParam.builder()
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
                log.info("语音合成成功, size={}bytes", bytes.length);
                return bytes;
            }
            log.warn("语音合成返回空数据");
            return null;
        } catch (Exception e) {
            log.error("语音合成失败", e);
            return null;
        }
    }

    public byte[] convertWavToAmr(byte[] wavBytes) {
        Path tmpWav = null;
        Path tmpAmr = null;
        try {
            tmpWav = Files.createTempFile("tts_", ".wav");
            tmpAmr = Files.createTempFile("tts_", ".amr");
            Files.write(tmpWav, wavBytes);

            ProcessBuilder pb = new ProcessBuilder(
                    FFMPEG, "-y",
                    "-i", tmpWav.toAbsolutePath().toString(),
                    "-ar", "8000", "-ac", "1",
                    "-c:a", "libopencore_amrnb",
                    "-b:a", "12.2k",
                    tmpAmr.toAbsolutePath().toString()
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            int code = p.waitFor();

            if (code == 0 && Files.size(tmpAmr) > 0) {
                byte[] amrBytes = Files.readAllBytes(tmpAmr);
                log.info("AMR 转码成功, wav={}bytes, amr={}bytes", wavBytes.length, amrBytes.length);
                return amrBytes;
            }
            log.warn("FFmpeg 转码失败, exitCode={}", code);
            return null;
        } catch (Exception e) {
            log.error("AMR 转码异常", e);
            return null;
        } finally {
            try { if (tmpWav != null) Files.deleteIfExists(tmpWav); } catch (Exception e) {}
            try { if (tmpAmr != null) Files.deleteIfExists(tmpAmr); } catch (Exception e) {}
        }
    }

    public int calculatePlayTimeMs(byte[] wavBytes) {
        if (wavBytes == null) return 0;
        return wavBytes.length * 8 / (24000 * 16 / 1000);
    }
}
