package com.husiyu.aipsy.controller;

import com.husiyu.aipsy.common.Result;
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
