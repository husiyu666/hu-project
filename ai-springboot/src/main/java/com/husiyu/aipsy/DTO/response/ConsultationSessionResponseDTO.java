package com.husiyu.aipsy.DTO.response;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConsultationSessionResponseDTO {
    // 会话ID
    private Long id;

    // 会话标题
    private String sessionTitle;

    // 开始时间
    private LocalDateTime startedAt;

    // 最后一条消息内容(用于会话列表预览)
    private String lastMessageContent;

    // 会话消息数
    private Integer messageCount;

    // 会话持续时长(分钟)
    private Long durationMinutes;
}
