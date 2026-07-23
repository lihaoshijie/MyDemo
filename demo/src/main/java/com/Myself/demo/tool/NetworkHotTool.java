package com.Myself.demo.tool;

import com.Myself.demo.service.NetworkHotService;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NetworkHotTool {

    private final NetworkHotService networkHotService;

    public NetworkHotTool(NetworkHotService networkHotService) {
        this.networkHotService = networkHotService;
    }

    @Tool(name = "network_hot", value = "查询全网实时热搜榜单，聚合微博、抖音、百度、网易等平台热点。当用户问热搜、热榜、热门话题、今日热点、最新热点事件时，调用此工具。")
    public String getNetworkHot() {
        log.info("查询全网热搜");
        return networkHotService.getHotList();
    }
}
