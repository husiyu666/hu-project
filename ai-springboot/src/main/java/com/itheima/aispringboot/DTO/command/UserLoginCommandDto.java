package com.itheima.aispringboot.DTO.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserLoginCommandDto {
    @NotBlank(message ="用户名不能为空" )
    @Size(max=100,message="用户名长度不规范")
    private String username;
    @NotBlank(message="密码不能为空")
    @Size(max=50,min=6,message = "密码长度不规范")
    private String password;
}
