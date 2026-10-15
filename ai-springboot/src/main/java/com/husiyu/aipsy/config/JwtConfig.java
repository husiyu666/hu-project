package com.husiyu.aipsy.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtConfig {
//    secret: MySecretKeyForJWT2025!@#$%^&*()_+SecureKeyHere
//    expiration: 86400000  # 24小时（毫秒）
//    refresh-expiration: 604800000  # 7天（毫秒）
//    header: Authorization  # token头部名称
//    token-prefix: "Bearer "  # token前缀
    private String secret;
    private Long expiration;
    private Long refreshExpiration;
    private String header;
    private String tokenPrefix;
}
