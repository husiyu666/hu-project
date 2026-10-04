package com.itheima.aispringboot.controller;

import com.itheima.aispringboot.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.Mapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("login")
public class Test {
    @GetMapping ("test")
        public Result<String> test(){
        return Result.ok("hello word");
        }
}
