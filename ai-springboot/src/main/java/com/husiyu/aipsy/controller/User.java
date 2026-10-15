package com.husiyu.aipsy.controller;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.husiyu.aipsy.DTO.command.UserLoginCommandDto;
import com.husiyu.aipsy.DTO.command.UserRegisterCommandDTO;
import com.husiyu.aipsy.DTO.response.UserLoginResponseDTO;
import com.husiyu.aipsy.common.Result;
import com.husiyu.aipsy.common.ResultCode;
import com.husiyu.aipsy.service.UserService;
import com.husiyu.aipsy.service.convert.UserConvert;
import com.husiyu.aipsy.util.JwtTokenUtil;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
public class User {
@Resource
private UserService userservice;
//用户登录
@PostMapping("/login")
    public Result<UserLoginResponseDTO> login(@Valid @RequestBody UserLoginCommandDto userLoginCommandDto){
    System.out.println(userLoginCommandDto.getUsername());
    System.out.println(userLoginCommandDto.getPassword());
    UserLoginResponseDTO result = userservice.login(userLoginCommandDto);
    System.out.println(result);
    return Result.ok(result);
}
//用户注册
@PostMapping("/add")
public Result<UserLoginResponseDTO.UserDetailResponseDTO> register(@Valid @RequestBody UserRegisterCommandDTO commandDTO){
    UserLoginResponseDTO.UserDetailResponseDTO result = userservice.register(commandDTO);
    return Result.ok(result);
}
//获取用户信息
@GetMapping("/current")
    public Result<UserLoginResponseDTO.UserDetailResponseDTO> getCurrent(){
    String token = JwtTokenUtil.getCurrentToken();
    DecodedJWT decodedJWT = JwtTokenUtil.verifyToken(token);
    Long userId = decodedJWT.getClaim("userId").asLong();
    //调用service层获取用户信息
    UserLoginResponseDTO.UserDetailResponseDTO userById = userservice.getUserById(userId);
    return Result.ok(userById);
}

//退出登录
    @PostMapping("/logout")
    public Result<?> logout(){
        String token = JwtTokenUtil.getCurrentToken();

    if(token==null|| token.trim().isEmpty()){
        return Result.error(ResultCode.PARAM_MISSING.getCode(),"token不能为空",null );
    }
    try{
        JwtTokenUtil.verifyToken(token);
    }catch(Exception e){
        return Result.error(ResultCode.TOKEN_INVALID.getCode(),ResultCode.TOKEN_INVALID.getMsg(),null);
    }
    return Result.ok("退出登录成功");
    }
}
