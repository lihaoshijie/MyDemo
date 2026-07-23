package com.Myself.demo.tool;

import com.Myself.demo.service.EightCharactersService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

/**
 *八字测算工具
 *功能： 1.精准排盘（四柱、天干地支、五行、藏干、十神、生肖、纳音、大运）
 *      2.格局分析
 *      3.流年运势
 *      4.五行喜忌（会结合神煞综合解读）
 */
@Component
public class EightCharactersTool {

    private final EightCharactersService eightCharactersService;

    public EightCharactersTool(EightCharactersService eightCharactersService) {
        this.eightCharactersService = eightCharactersService;
    }

    @Tool(name = "eight_characters", value = "根据出生年月日时（精确到分钟更准）推算四柱八字，包含天干地支、五行、藏干、十神、生肖、时辰、大运。如果用户没有提供出生时辰，请主动询问；用户说不知道时使用默认值12点（午时）。计算大运需要性别，如果用户没有提供性别，请主动询问。")
    public String execute(
            @P("出生年份，如：2000") int year,
            @P("出生月份，1~12") int month,
            @P("出生日期，1~31") int day,
            @P("出生小时，0~23。如果用户不知道自己的出生时辰，使用默认值12") int hour,
            @P("出生分钟，0~59。可选参数，默认0") int minute,
            @P("性别，男或女。计算大运必需。") String gender) {
        return eightCharactersService.calculate(year, month, day, hour, minute, gender);
     }

    @Tool(name = "annual_fortune", value = "根据出生信息推算当前年份的流年运势，分析今年（2026年丙午年）天干地支与原局四柱的冲合刑害关系。需要性别才能显示当前大运。如果用户没有提供出生时辰，请主动询问；用户说不知道时使用默认值12点（午时）。")
    public String annualFortune(
            @P("出生年份，如：2000") int year,
            @P("出生月份，1~12") int month,
            @P("出生日期，1~31") int day,
            @P("出生小时，0~23。如果用户不知道自己的出生时辰，使用默认值12") int hour,
            @P("出生分钟，0~59。可选参数，默认0") int minute,
            @P("性别，男或女。计算当前大运需要。如果用户没有提供可以不传") String gender) {
        return eightCharactersService.calculateAnnualFortune(year, month, day, hour, minute, gender);
    }

    @Tool(name = "element_preference", value = "分析八字五行喜忌、推算日主旺衰、喜用神和忌神，给出五行调整建议。不需要性别。如果用户没有提供出生时辰，请主动询问；用户说不知道时使用默认值12点（午时）。")
    public String elementPreference(
            @P("出生年份，如：2000") int year,
            @P("出生月份，1~12") int month,
            @P("出生日期，1~31") int day,
            @P("出生小时，0~23。如果用户不知道自己的出生时辰，使用默认值12") int hour,
            @P("出生分钟，0~59。可选参数，默认0") int minute) {
        return eightCharactersService.calculateElementPreference(year, month, day, hour, minute);
    }
}
