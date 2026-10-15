package com.husiyu.aipsy.util;

import cn.hutool.json.JSONUtil;
import com.husiyu.aipsy.DTO.response.UserLoginResponseDTO;
import com.husiyu.aipsy.common.ResultCode;
import com.husiyu.aipsy.config.SecurityConfig;
import com.husiyu.aipsy.enumclass.UserStatus;
import com.husiyu.aipsy.service.UserService;
import com.auth0.jwt.exceptions.JWTVerificationException;
import jakarta.annotation.Resource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class JwtAuthticationFilter extends OncePerRequestFilter {
    @Resource
    private UserService userService;
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        //获取请求的URI
        String requestURI = request.getRequestURI();
        //获取请求的方法
        String method = request.getMethod();
        System.out.println(requestURI);
        System.out.println(method);
        //1.提取  JWT token
        String token = JwtTokenUtil.extractTokenFromRequest(request);
        if(StringUtils.hasText(token)){
         //2.验证token并获取用户信息(校验失败会抛JWTVerificationException,需捕获后返回401而不是500)
            JwtTokenUtil.TokenVerificationResult validationResult;
            try{
                validationResult = JwtTokenUtil.validateToken(token);
            }catch (JWTVerificationException e){
                clearSecuritycontext();
                ResponseUtil.writeError(response, ResultCode.TOKEN_INVALID);
                return;
            }
            if(validationResult!=null && validationResult.isValid()){
               //3.查询用户信息验证用户状态
                UserLoginResponseDTO.UserDetailResponseDTO user = userService.getUserById(validationResult.getUserId());
                System.out.println(JSONUtil.parseObj(user));
                if(user!=null && UserStatus.NORMAL.getCode().equals(user.getStatus())){
                    //创建spring Security安全认证对象
                    List<SimpleGrantedAuthority> authorities = Collections.singletonList(
                            new SimpleGrantedAuthority("ROLE_" + validationResult.getRoleType())
                    );
                    //5.创建UsernamePasswordAuthenticationToken对象
                    UsernamePasswordAuthenticationToken authcation = new UsernamePasswordAuthenticationToken(
                            validationResult.getUsername(),//用户名作为主体
                            null,
                            authorities
                    );
                    //6.设置认证信息到Spring Security
                    SecurityContextHolder.getContext().setAuthentication(authcation);

                    //将token存储到请求属性中
                    request.setAttribute("jwtToken",token);
                    // ✅认证成功，放行请求，交给后续过滤器、controller
                    filterChain.doFilter(request, response);
                    // 请求处理完毕，清理当前线程安全上下文
                    clearSecuritycontext();
                    return;
                }
                else {
                    clearSecuritycontext();
                    ResponseUtil.writeError(response, ResultCode.TOKEN_ACCESS_FORBIDDEN);
                }
            }
            else {
                //清理上下文
                clearSecuritycontext();
                ResponseUtil.writeError(response, ResultCode.TOKEN_INVALID);
            }
        }
        //清理上下文
        clearSecuritycontext();
        ResponseUtil.writeError(response, ResultCode.ACCESS_UNAUTHORIZED);
    }

    //清理spring security上下文
    private void clearSecuritycontext(){
        SecurityContextHolder.clearContext();
    }

    @Override
    protected  boolean shouldNotFilter(HttpServletRequest request){
        String requestUri=request.getRequestURI();
        //检查是否为公开路径
        return SecurityConfig.isPublicPATH(requestUri);
    }

}
