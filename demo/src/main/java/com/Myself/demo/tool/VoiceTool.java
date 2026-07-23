package com.Myself.demo.tool;

import com.Myself.demo.service.VoicePreferenceService;
import com.Myself.demo.service.VoiceType;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

@Component
public class VoiceTool {

    private final VoicePreferenceService voicePreferenceService;

    public VoiceTool(VoicePreferenceService voicePreferenceService) {
        this.voicePreferenceService = voicePreferenceService;
    }

    @Tool(name = "switch_voice", value = "切换语音播报的音色。当用户想换声音、觉得声音不好听、想用特定音色（如女声冷静、女声元气、童声等）时，调用此工具。可用音色：默认男声、女声元气、女声欢脱、女声童声、女声冷静、女声共情、女声知性。")
    public String switchVoice(
            @P("音色名称，如：女声冷静、女声元气、童声、默认男声、女声知性、女声共情、女声欢脱") String voiceName,
            @P("用户ID，系统自动填充") String userId) {
        try {
            VoiceType vt = VoiceType.fromName(voiceName);
            voicePreferenceService.setVoiceCode(userId, vt.getCode());
            return "已切换音色为 " + vt.getDescription();
        } catch (Exception e) {
            return "音色切换失败，请重试";
        }
    }

    @Tool(name = "enable_voice", value = "为本次回复启用语音播报。仅本次生效，下次自动关闭。当用户要求用语音回复、语音告诉我、语音总结时使用。如果用户没有明确要求语音，不要调用此工具。")
    public String enableVoice(@P("用户ID，系统自动填充") String userId) {
        voicePreferenceService.enableVoice(userId);
        return "语音播报已开启";
    }

    @Tool(name = "disable_voice", value = "关闭语音播报模式。当用户不想听语音、说关闭语音、关掉语音、别念了时使用。")
    public String disableVoice(@P("用户ID，系统自动填充") String userId) {
        voicePreferenceService.disableVoice(userId);
        return "语音播报已关闭";
    }

    @Tool(name = "list_voice_types", value = "列出所有可用的语音播报音色。当用户问有哪些音色、音色列表时使用。")
    public String listVoiceTypes() {
        StringBuilder sb = new StringBuilder("可用音色：\n");
        for (VoiceType vt : VoiceType.values()) {
            sb.append(vt.getDescription()).append("\n");
        }
        return sb.toString();
    }
}
