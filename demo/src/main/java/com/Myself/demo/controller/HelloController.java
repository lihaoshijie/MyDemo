package com.Myself.demo.controller;

import com.Myself.demo.entity.TUser;
import com.Myself.demo.mapper.TUserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.annotation.Resource;
import java.util.List;

@Slf4j
@RestController
public class HelloController {

    private static final String API_KEY = System.getenv("A_BAILIAN_API_KEY");

    @Resource
    private TUserMapper tUserMapper;

    @GetMapping("/hello")
    public String hello(){
        log.info("访问 /hello 接口");
        log.info("API_KEY: {}", API_KEY);
        return "SpringBoot项目运行正常！";
    }

    @GetMapping("/user/list")
    public List<TUser> userList(){
        log.info("访问 /user/list 接口");
        List<TUser> list = tUserMapper.findAll();
        log.info("查询到 {} 条用户记录", list.size());
        return list;
    }
}
