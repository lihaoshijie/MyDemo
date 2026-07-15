package com.Myself.demo.controller;

import com.Myself.demo.entity.TUser;
import com.Myself.demo.mapper.TUserMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.annotation.Resource;
import java.util.List;

@RestController
public class HelloController {

    @Resource
    private TUserMapper tUserMapper;

    @GetMapping("/hello")
    public String hello(){
        return "SpringBoot项目运行正常！";
    }

    @GetMapping("/user/list")
    public List<TUser> userList(){
        return tUserMapper.findAll();
    }
}
