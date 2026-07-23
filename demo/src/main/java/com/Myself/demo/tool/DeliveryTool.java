package com.Myself.demo.tool;

import com.Myself.demo.command.Command;
import com.Myself.demo.exception.MissingParameterException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

@Slf4j
@Component
public class DeliveryTool implements Command {

    private static final String API_URL = "https://api.kdniao.com/Ebusiness/EbusinessOrderHandle.aspx";

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${delivery.app-id:}")
    private String appId;

    @Value("${delivery.api-key:}")
    private String apiKey;

    @Override
    public String getName() {
        return "delivery";
    }

    @Override
    public String execute(String[] args) {
        if (args.length < 2) {
            throw new MissingParameterException("用法: delivery <快递公司> <单号> [手机尾号后4位]");
        }
        String phone = args.length > 2 ? args[2] : "";
        return doQuery(args[0], args[1], phone);
    }

    @Tool(name = "query_delivery", value = "查询快递物流信息。需要快递公司名称和运单号。注意：中通、申通快递必须同时提供收件人手机尾号后4位，否则查询失败。")
    public String queryDelivery(
            @P("快递公司名称，如：顺丰、中通、圆通、韵达、京东、邮政") String company,
            @P("快递运单号") String trackingNumber,
            @P("收件人手机尾号后4位。重要：查询中通、申通快递时必须提供此参数，否则查询失败") String phoneLastFour) {
        return doQuery(company, trackingNumber, phoneLastFour);
    }

    private String doQuery(String company, String trackingNumber, String phoneLastFour) {
        if (appId == null || appId.isEmpty() || apiKey == null || apiKey.isEmpty()) {
            return "快递查询服务未配置，请联系管理员设置 delivery.app-id 和 delivery.api-key";
        }
        try {
            String companyCode = getCompanyCode(company);
            String requestData = phoneLastFour != null && !phoneLastFour.isEmpty()
                    ? "{'OrderCode':'','ShipperCode':'" + companyCode + "','LogisticCode':'" + trackingNumber + "','CustomerName':'" + phoneLastFour + "'}"
                    : "{'OrderCode':'','ShipperCode':'" + companyCode + "','LogisticCode':'" + trackingNumber + "'}";
            String requestType = "8002";
            String dataSign = base64(md5(requestData + apiKey));
            String body = "RequestData=" + URLEncoder.encode(requestData, StandardCharsets.UTF_8)
                    + "&EBusinessID=" + URLEncoder.encode(appId, StandardCharsets.UTF_8)
                    + "&RequestType=" + requestType
                    + "&DataSign=" + URLEncoder.encode(dataSign, StandardCharsets.UTF_8)
                    + "&DataType=2";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .header("Content-Type", "application/x-www-form-urlencoded;charset=utf-8")
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String respBody = response.body();
            log.info("快递鸟API响应: {}", respBody.length() > 200 ? respBody.substring(0, 200) : respBody);

            JsonNode root = objectMapper.readTree(respBody);
            if (!root.get("Success").asBoolean()) {
                return "查询失败: " + root.get("Reason").asText();
            }

            JsonNode traces = root.get("Traces");
            if (traces == null || !traces.isArray() || traces.isEmpty()) {
                return "暂无物流信息";
            }

            String stateText = switch (root.get("State").asText()) {
                case "0" -> "无轨迹";
                case "1" -> "已揽收";
                case "2" -> "在途中";
                case "3" -> "已签收";
                case "4" -> "问题件";
                default -> "未知";
            };

            StringBuilder sb = new StringBuilder("📦 ")
                    .append(company).append(" · ").append(trackingNumber)
                    .append(" [").append(stateText).append("]\n\n");
            int count = 0;
            for (JsonNode item : traces) {
                String time = item.get("AcceptTime").asText();
                String station = item.get("AcceptStation").asText();
                sb.append("▸ ").append(station).append("\n  ").append(time).append("\n");
                count++;
                if (count >= 10) {
                    sb.append("...仅显示最近10条记录");
                    break;
                }
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("快递查询失败", e);
            return "快递查询失败: " + e.getMessage();
        }
    }

    private static String md5(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    private static String base64(String input) {
        return Base64.getEncoder().encodeToString(input.getBytes(StandardCharsets.UTF_8));
    }

    private static String getCompanyCode(String name) {
        return switch (name) {
            case "顺丰" -> "SF";
            case "中通" -> "ZTO";
            case "圆通" -> "YTO";
            case "韵达" -> "YD";
            case "京东" -> "JD";
            case "邮政", "EMS" -> "EMS";
            case "申通" -> "STO";
            case "极兔" -> "JTSD";
            case "德邦" -> "DBL";
            case "菜鸟" -> "CNS";
            case "天天" -> "TTKD";
            case "韵达快运" -> "YDKY";
            case "顺丰快运" -> "S FF";
            case "百世" -> "HTKY";
            default -> name;
        };
    }
}
