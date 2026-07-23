package com.Myself.demo.service;

import com.Myself.demo.model.WeatherResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts raw weather data into stable, user-facing advice.
 *
 * <p>The analyser is deliberately stateless so it can be used from commands,
 * services or tests without creating another Spring bean.</p>
 */
public final class SmartWeatherService {

    private static final Pattern NUMBER_PATTERN = Pattern.compile("[-+]?\\d+(?:\\.\\d+)?");

    private SmartWeatherService() {
        throw new AssertionError("Utility class must not be instantiated");
    }

    /**
     * Analyses both real-time and forecast responses. Missing values are
     * tolerated and simply do not participate in the corresponding rule.
     */
    public static WeatherAnalysis analyse(WeatherResponse weather) {
        if (weather == null) {
            throw new IllegalArgumentException("weather must not be null");
        }

        String condition = clean(weather.getWeather());
        OptionalDouble temperature = effectiveTemperature(weather);
        OptionalDouble feelsLike = number(weather.getFeelsLike());
        OptionalDouble humidity = number(weather.getHumidity());
        OptionalDouble windScale = number(weather.getWindScale());
        OptionalDouble visibility = number(weather.getVisibility());

        boolean umbrella = needsUmbrella(condition);
        RiskLevel risk = determineRisk(condition, temperature, windScale, visibility);
        String dressing = dressingAdvice(temperature, feelsLike, condition);
        String travel = travelAdvice(condition, windScale, visibility, umbrella, risk);
        String health = healthAdvice(temperature, feelsLike, humidity, condition);

        List<String> alerts = new ArrayList<>();
        collectAlerts(alerts, condition, temperature, humidity, windScale, visibility);

        return new WeatherAnalysis(
                locationOf(weather), condition, temperature, feelsLike, humidity,
                windScale, visibility, umbrella, risk, dressing, travel, health,
                List.copyOf(alerts));
    }

    /** American spelling alias for callers that prefer it. */
    public static WeatherAnalysis analyze(WeatherResponse weather) {
        return analyse(weather);
    }

    /** Builds a compact report suitable for chat output. */
    public static String formatSmartReport(WeatherResponse weather) {
        WeatherAnalysis result = analyse(weather);
        StringBuilder report = new StringBuilder();
        report.append(result.location()).append("天气分析");
        if (!result.condition().isEmpty()) {
            report.append("（").append(result.condition()).append("）");
        }
        report.append('\n').append("风险等级：").append(result.riskLevel().label());
        report.append('\n').append("穿衣建议：").append(result.dressingAdvice());
        report.append('\n').append("出行建议：").append(result.travelAdvice());
        report.append('\n').append("健康提示：").append(result.healthAdvice());
        if (!result.alerts().isEmpty()) {
            report.append('\n').append("重点提醒：").append(String.join("；", result.alerts()));
        }
        return report.toString();
    }

    public static boolean needsUmbrella(WeatherResponse weather) {
        return weather != null && needsUmbrella(clean(weather.getWeather()));
    }

    public static boolean isSevereWeather(WeatherResponse weather) {
        if (weather == null) {
            return false;
        }
        RiskLevel level = analyse(weather).riskLevel();
        return level == RiskLevel.WARNING || level == RiskLevel.DANGER;
    }

    public static String getDressingAdvice(WeatherResponse weather) {
        return analyse(weather).dressingAdvice();
    }

    public static String getTravelAdvice(WeatherResponse weather) {
        return analyse(weather).travelAdvice();
    }

    public static String getHealthAdvice(WeatherResponse weather) {
        return analyse(weather).healthAdvice();
    }

    /** Extracts the first number from values such as "23°C", "3级" or "5 km/h". */
    public static OptionalDouble parseNumber(String value) {
        return number(value);
    }

    private static RiskLevel determineRisk(String condition, OptionalDouble temperature,
                                           OptionalDouble windScale, OptionalDouble visibility) {
        String text = normalized(condition);
        if (containsAny(text, "台风", "龙卷", "冰雹", "暴雪", "特大暴雨", "tornado", "typhoon", "hail")) {
            return RiskLevel.DANGER;
        }
        if (containsAny(text, "雷暴", "暴雨", "冻雨", "沙尘暴", "大雪", "thunderstorm", "heavy rain", "blizzard")
                || atLeast(windScale, 8) || atMost(visibility, 1)
                || atLeast(temperature, 40) || atMost(temperature, -15)) {
            return RiskLevel.WARNING;
        }
        if (containsAny(text, "雷", "雨", "雪", "雾", "霾", "沙尘", "sleet", "rain", "snow", "fog", "haze")
                || atLeast(windScale, 6) || atMost(visibility, 3)
                || atLeast(temperature, 35) || atMost(temperature, -5)) {
            return RiskLevel.NOTICE;
        }
        return RiskLevel.NORMAL;
    }

    private static String dressingAdvice(OptionalDouble temperature, OptionalDouble feelsLike,
                                         String condition) {
        OptionalDouble perceived = feelsLike.isPresent() ? feelsLike : temperature;
        if (perceived.isEmpty()) {
            return "根据体感温度灵活增减衣物。";
        }

        double value = perceived.getAsDouble();
        String advice;
        if (value >= 32) {
            advice = "穿轻薄透气衣物，注意遮阳和散热。";
        } else if (value >= 25) {
            advice = "短袖等清凉衣物较合适。";
        } else if (value >= 18) {
            advice = "薄外套或长袖较合适，早晚可适当添衣。";
        } else if (value >= 10) {
            advice = "建议穿夹克、卫衣或针织衫。";
        } else if (value >= 0) {
            advice = "建议穿厚外套并注意保暖。";
        } else {
            advice = "天气严寒，建议羽绒服、帽子和手套等完整保暖。";
        }

        if (containsAny(normalized(condition), "雨", "雪", "rain", "snow", "sleet")) {
            advice += " 鞋袜宜防水防滑。";
        }
        return advice;
    }

    private static String travelAdvice(String condition, OptionalDouble windScale,
                                       OptionalDouble visibility, boolean umbrella,
                                       RiskLevel risk) {
        List<String> advice = new ArrayList<>();
        String text = normalized(condition);

        if (risk == RiskLevel.DANGER) {
            advice.add("非必要不要外出，并留意当地预警");
        } else if (risk == RiskLevel.WARNING) {
            advice.add("尽量减少户外活动并及时查看预警");
        }
        if (umbrella) {
            advice.add(containsAny(text, "雪", "snow", "sleet") ? "注意路面湿滑或结冰" : "随身携带雨具");
        }
        if (atLeast(windScale, 6)) {
            advice.add("远离广告牌、树木和临时搭建物");
        }
        if (atMost(visibility, 3) || containsAny(text, "雾", "霾", "fog", "haze")) {
            advice.add("驾车请减速、开灯并增大车距");
        }
        if (advice.isEmpty()) {
            advice.add("天气对出行影响较小，按日常安排即可");
        }
        return String.join("；", advice) + "。";
    }

    private static String healthAdvice(OptionalDouble temperature, OptionalDouble feelsLike,
                                       OptionalDouble humidity, String condition) {
        List<String> advice = new ArrayList<>();
        OptionalDouble perceived = feelsLike.isPresent() ? feelsLike : temperature;
        String text = normalized(condition);

        if (atLeast(perceived, 32)) {
            advice.add("及时补水，避免长时间暴晒和高强度运动");
        } else if (atMost(perceived, 5)) {
            advice.add("注意头颈和手脚保暖，谨防受凉");
        }
        if (atLeast(humidity, 85)) {
            advice.add("湿度较高，室内注意通风除湿");
        } else if (humidity.isPresent() && humidity.getAsDouble() < 30) {
            advice.add("空气干燥，注意补水和皮肤保湿");
        }
        if (containsAny(text, "霾", "沙尘", "haze", "dust")) {
            advice.add("敏感人群应减少户外停留，外出可佩戴口罩");
        }
        if (advice.isEmpty()) {
            advice.add("体感条件总体正常，保持规律补水即可");
        }
        return String.join("；", advice) + "。";
    }

    private static void collectAlerts(List<String> alerts, String condition,
                                      OptionalDouble temperature, OptionalDouble humidity,
                                      OptionalDouble windScale, OptionalDouble visibility) {
        String text = normalized(condition);
        if (containsAny(text, "雷", "thunder")) alerts.add("雷电天气请远离高处、水边和金属设施");
        if (containsAny(text, "暴雨", "heavy rain")) alerts.add("警惕积水、山洪和地质灾害");
        if (containsAny(text, "雪", "冻雨", "snow", "sleet")) alerts.add("道路可能结冰，注意防滑");
        if (atLeast(temperature, 35)) alerts.add("高温时段注意防暑");
        if (atMost(temperature, -5)) alerts.add("低温环境注意防冻");
        if (atLeast(humidity, 90)) alerts.add("高湿环境体感闷热或阴冷");
        if (atLeast(windScale, 6)) alerts.add("风力较强，注意高空坠物");
        if (atMost(visibility, 3)) alerts.add("能见度较低，交通出行需谨慎");
    }

    private static OptionalDouble effectiveTemperature(WeatherResponse weather) {
        OptionalDouble current = number(weather.getTemperature());
        if (current.isPresent()) return current;

        OptionalDouble high = number(weather.getHigh());
        OptionalDouble low = number(weather.getLow());
        if (high.isPresent() && low.isPresent()) {
            return OptionalDouble.of((high.getAsDouble() + low.getAsDouble()) / 2.0);
        }
        return high.isPresent() ? high : low;
    }

    private static boolean needsUmbrella(String condition) {
        return containsAny(normalized(condition),
                "雨", "雪", "冰雹", "sleet", "rain", "shower", "snow", "hail");
    }

    private static OptionalDouble number(String value) {
        if (value == null || value.isBlank()) return OptionalDouble.empty();
        Matcher matcher = NUMBER_PATTERN.matcher(value.replace(',', '.'));
        if (!matcher.find()) return OptionalDouble.empty();
        try {
            return OptionalDouble.of(Double.parseDouble(matcher.group()));
        } catch (NumberFormatException ignored) {
            return OptionalDouble.empty();
        }
    }

    private static String locationOf(WeatherResponse weather) {
        List<String> parts = new ArrayList<>();
        addDistinct(parts, weather.getCountry());
        addDistinct(parts, weather.getProvince());
        if (weather.getDistrict() != null && !weather.getDistrict().isBlank()) {
            addDistinct(parts, weather.getDistrict());
        }
        addDistinct(parts, weather.getCity());
        return parts.isEmpty() ? "当前地区" : String.join(" ", parts);
    }

    private static void addDistinct(List<String> parts, String value) {
        String cleaned = clean(value);
        if (!cleaned.isEmpty() && !parts.contains(cleaned)) parts.add(cleaned);
    }

    private static boolean atLeast(OptionalDouble value, double threshold) {
        return value.isPresent() && value.getAsDouble() >= threshold;
    }

    private static boolean atMost(OptionalDouble value, double threshold) {
        return value.isPresent() && value.getAsDouble() <= threshold;
    }

    private static boolean containsAny(String value, String... keywords) {
        for (String keyword : keywords) {
            if (value.contains(keyword)) return true;
        }
        return false;
    }

    private static String normalized(String value) {
        return clean(value).toLowerCase(Locale.ROOT);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    public enum RiskLevel {
        NORMAL("正常"),
        NOTICE("注意"),
        WARNING("警告"),
        DANGER("危险");

        private final String label;

        RiskLevel(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public record WeatherAnalysis(
            String location,
            String condition,
            OptionalDouble temperature,
            OptionalDouble feelsLike,
            OptionalDouble humidity,
            OptionalDouble windScale,
            OptionalDouble visibility,
            boolean umbrellaNeeded,
            RiskLevel riskLevel,
            String dressingAdvice,
            String travelAdvice,
            String healthAdvice,
            List<String> alerts) {
    }
}
