package com.husiyu.aipsy.service;

import ch.qos.logback.classic.spi.EventArgUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.husiyu.aipsy.DTO.command.UserLoginCommandDto;
import com.husiyu.aipsy.DTO.command.UserRegisterCommandDTO;
import com.husiyu.aipsy.DTO.response.UserLoginResponseDTO;
import com.husiyu.aipsy.common.Result;
import com.husiyu.aipsy.entity.User;
import com.husiyu.aipsy.enumclass.UserType;
import com.husiyu.aipsy.exception.BusinessException;
import com.husiyu.aipsy.mapper.UserMapper;
import com.husiyu.aipsy.service.convert.UserConvert;
import com.husiyu.aipsy.util.JwtTokenUtil;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    @Resource
   private UserMapper userMapper;

    //用户登录
  private final BCryptPasswordEncoder bcpassword = new BCryptPasswordEncoder();
    public UserLoginResponseDTO login(UserLoginCommandDto userLoginCommandDto){
        //构建查询条件，MyBatis‑Plus 的条件构造器，用于组装 SQL 查询条件，用 Lambda 写法，不用写字符串列名，防止写错字段名。
        LambdaQueryWrapper<User> objectLambdaQueryWrapper = new LambdaQueryWrapper<>();
           objectLambdaQueryWrapper.eq(User::getUsername,userLoginCommandDto.getUsername())
                   .or()
                   .eq(User::getEmail,userLoginCommandDto.getUsername());
           //调用MP API查询
        User user=userMapper.selectOne(objectLambdaQueryWrapper);
        System.out.println(user);
         //校验用户是否存在
        if(user == null){
            throw new BusinessException("用户名不存在");
        }
        //校验密码
        String pass = userLoginCommandDto.getPassword().trim();
        if(!bcpassword.matches(pass,user.getPassword())){
            throw new BusinessException("用户名或者密码错误");
        }
        //判断用户状态
        if(!user.isActive()){
            throw new BusinessException("用户状态异常，请联系管理员");
        }
        //生成token
      String token= JwtTokenUtil.generateToken(user.getId(), user.getUsername(), user.getUserType());
        System.out.println(token);
        UserLoginResponseDTO.UserDetailResponseDTO userDetailResponseDTO = UserConvert.entityToDetailResponse(user);
        UserLoginResponseDTO userLoginResponseDTO = UserConvert.buildLoginResponse(token, userDetailResponseDTO);

        return userLoginResponseDTO;
    }

    //注册账号
    public UserLoginResponseDTO.UserDetailResponseDTO register(UserRegisterCommandDTO commandDTO){
        //检查用户是否存在
        LambdaQueryWrapper<User>  usernamelambdaQuery = new LambdaQueryWrapper<>();
        usernamelambdaQuery.eq(User::getUsername,commandDTO.getUsername());
        if(userMapper.selectCount(usernamelambdaQuery)>0){
            throw new BusinessException("用户已存在");
        }
        //检查邮箱是否存在
        LambdaQueryWrapper<User> emailLambdaQuery = new LambdaQueryWrapper<>();
        emailLambdaQuery.eq(User::getEmail,commandDTO.getEmail());
        if(userMapper.selectCount(emailLambdaQuery)>0){
            throw new BusinessException("邮箱已存在");
        }
        //检查用户类型
        if(!UserType.isValidCode(commandDTO.getUserType())){
            throw new BusinessException("用户类型不对");
        }
        //检查手机是否存在
        //创建用户
        String password = commandDTO.getPassword().trim();
        String encodepassword = bcpassword.encode(password);
        User user = UserConvert.registerCommandToEntity(commandDTO, encodepassword);
         userMapper.insert(user);

        return UserConvert.entityToDetailResponse(user);
    }

    //根据用户id查找用户
    public UserLoginResponseDTO.UserDetailResponseDTO getUserById(Long id){
        User user = userMapper.selectById(id);
        if(user== null){
            throw new BusinessException("用户不存在");
        }
        return UserConvert.entityToDetailResponse(user);
    }
}
