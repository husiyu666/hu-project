package com.husiyu.aipsy.service;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.husiyu.aipsy.DTO.command.ConsultationSessionCreateDTO;
import com.husiyu.aipsy.DTO.response.ConsultationSessionResponseDTO;
import com.husiyu.aipsy.entity.ConsultationMessage;
import com.husiyu.aipsy.entity.ConsultationSession;
import com.husiyu.aipsy.entity.User;
import com.husiyu.aipsy.exception.BusinessException;
import com.husiyu.aipsy.mapper.ConsultationsessionMapper;
import com.husiyu.aipsy.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ConsultationsessionService {
    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ConsultationsessionMapper consultationsessionMapper;

    @Autowired
    private ConsultationMessageService consultationMessageService;

    public ConsultationSession createSession(Long userId, ConsultationSessionCreateDTO createDTO){
        //验证用户是否存在
        User user = userMapper.selectById(userId);
        if(user == null){
            throw new RuntimeException("用户不存在, userId=" + userId);
        }
        ConsultationSession session = ConsultationSession.builder()
                .userId(userId)
                .sessionTitle(createDTO.getSessionTitle())
                .startedAt(LocalDateTime.now())
                .build();
        //如果未提供标题
        if(StrUtil.isBlank(createDTO.getSessionTitle())){
            session.setSessionTitle("小暖AI助手-"+ DateUtil.format(LocalDateTime.now(),"MM-dd HH:mm"));
        }
        //插入会话记录
        consultationsessionMapper.insert(session);
        return session;
    }

    //分页查询当前用户的历史会话列表
    public Page<ConsultationSessionResponseDTO> getSessionPage(Long userId, Integer pageNum, Integer pageSize, String emotionTag){
        LambdaQueryWrapper<ConsultationSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConsultationSession::getUserId,userId)
                .orderByDesc(ConsultationSession::getStartedAt);
        //按情绪标签过滤(可选)
        if(StrUtil.isNotBlank(emotionTag)){
            wrapper.apply("id IN (SELECT session_id FROM consultation_message WHERE emotion_tag = {0})", emotionTag);
        }
        //分页查询会话
        Page<ConsultationSession> page = consultationsessionMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);

        //一次性查出本页所有会话的消息,在内存中聚合,避免逐会话查询(N+1)
        List<Long> sessionIds = page.getRecords().stream().map(ConsultationSession::getId).collect(Collectors.toList());
        Map<Long, List<ConsultationMessage>> messagesBySessionId = consultationMessageService.getMessagesBySessionIds(sessionIds)
                .stream()
                .collect(Collectors.groupingBy(ConsultationMessage::getSessionId));

        //会话实体转响应DTO,并填充消息统计信息
        List<ConsultationSessionResponseDTO> records = page.getRecords().stream()
                .map(session -> convertToResponseDTO(session, messagesBySessionId.get(session.getId())))
                .collect(Collectors.toList());

        Page<ConsultationSessionResponseDTO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(records);
        return result;
    }

    //保存情绪分析结果到会话
    public void updateEmotionAnalysis(Long sessionId, String analysisJson){
        ConsultationSession session = ConsultationSession.builder()
                .id(sessionId)
                .lastEmotionAnalysis(analysisJson)
                .lastEmotionUpdatedAt(LocalDateTime.now())
                .build();
        consultationsessionMapper.updateById(session);
    }

    //校验会话归属:会话必须存在且属于当前用户,返回会话实体
    public ConsultationSession getOwnedSession(Long userId, Long sessionId){
        ConsultationSession session = consultationsessionMapper.selectById(sessionId);
        if(session == null){
            throw new BusinessException("会话不存在");
        }
        if(!session.getUserId().equals(userId)){
            throw new BusinessException("无权访问该会话");
        }
        return session;
    }

    //删除会话(连同会话下的全部消息,放在同一事务中)
    @Transactional(rollbackFor = Exception.class)
    public void deleteSession(Long userId, Long sessionId){
        //校验会话归属,防止越权删除他人会话
        getOwnedSession(userId, sessionId);
        //先删除会话下的消息,再删除会话
        consultationMessageService.deleteBySessionId(sessionId);
        consultationsessionMapper.deleteById(sessionId);
    }

    //会话实体转响应DTO,根据会话消息填充统计字段
    private ConsultationSessionResponseDTO convertToResponseDTO(ConsultationSession session, List<ConsultationMessage> messages){
        ConsultationSessionResponseDTO responseDTO = new ConsultationSessionResponseDTO();
        responseDTO.setId(session.getId());
        responseDTO.setSessionTitle(session.getSessionTitle());
        responseDTO.setStartedAt(session.getStartedAt());

        //无消息的会话(理论上不会出现,防御性处理)
        if(messages == null || messages.isEmpty()){
            responseDTO.setLastMessageContent("");
            responseDTO.setMessageCount(0);
            responseDTO.setDurationMinutes(0L);
            return responseDTO;
        }

        //按时间排序后取第一条和最后一条
        List<ConsultationMessage> sorted = messages.stream()
                .sorted(Comparator.comparing(ConsultationMessage::getCreatedAt))
                .collect(Collectors.toList());
        ConsultationMessage last = sorted.get(sorted.size() - 1);

        responseDTO.setLastMessageContent(last.getContent());
        responseDTO.setMessageCount(sorted.size());
        //持续时长 = 最后一条消息时间 - 第一条消息时间(分钟)
        responseDTO.setDurationMinutes(Duration.between(sorted.get(0).getCreatedAt(), last.getCreatedAt()).toMinutes());
        return responseDTO;
    }
}
