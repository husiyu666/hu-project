package com.husiyu.aipsy.DTO.response;

import com.husiyu.aipsy.DTO.command.UserLoginCommandDto;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Builder
@Data
public class UserLoginResponseDTO {
    private String token;
    private String roleType;
    private UserDetailResponseDTO userInfo;
//     "id": 71,
//             "username": "string",
//             "email": "string",
//             "nickname": "string",
//             "phone": "string",
//             "gender": 0,
//             "genderDisplayName": "未知",
//             "userType": 1,
//             "userTypeDisplayName": "普通用户",
//             "status": 1,
//             "statusDisplayName": "正常",
//             "displayName": "string",
//             "createdAt": "2026-03-06 21:17:50",
//             "updatedAt": "2026-03-06 21:17:50"
    @Data
    @Builder
    //有static在外部类可直接new
    public static class UserDetailResponseDTO{
        private Long id;
        private String username;
        private String email;
        private String nickname;
        private String avatar;
        private String phone;
        private Integer gender;
        private String genderDisplayName;
        private Integer userType;
        private String userTypeDisplayName;
        private LocalDate birthday;
        private Integer status;
        private String statusDisplayName;
        private String displayName;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
