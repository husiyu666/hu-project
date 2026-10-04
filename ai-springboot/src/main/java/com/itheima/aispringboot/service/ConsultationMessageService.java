package com.itheima.aispringboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itheima.aispringboot.DTO.response.ConsultationMessageResponseDTO;
import com.itheima.aispringboot.entity.ConsultationMessage;
import com.itheima.aispringboot.mapper.ConsultationMessageMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ConsultationMessageService {
    @Autowired
    private ConsultationMessageMapper consultationMessageMapper;

    public ConsultationMessage saveUserMessage(Long sessionId,String content,String emotion_tag){
        ConsultationMessage message = ConsultationMessage.builder()
                .sessionId(sessionId)
                .senderType(1)
                .messageType(1)
                .content(content)
                .emotionTag(emotion_tag)
                .createdAt(LocalDateTime.now())
                .build();
        //保存用户消息到数据库
        consultationMessageMapper.insert(message);
        return  message;
    }

    public ConsultationMessage saveAimessage(Long sessionId,String content,String aiModel){
        ConsultationMessage message = ConsultationMessage.builder()
                .sessionId(sessionId)
                .senderType(2)
                .messageType(1)
                .content(content)
                .aiModel(aiModel)
                .createdAt(LocalDateTime.now())
                .build();
        consultationMessageMapper.insert(message);
        return message;
    }

    public Integer getMessageCountBysessionId(Long sessionId){
        LambdaQueryWrapper<ConsultationMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConsultationMessage::getSessionId,sessionId);
        Long l = consultationMessageMapper.selectCount(wrapper);
        return l.intValue();
    }

    //查询会话的全部消息(按时间正序,用于历史对话回放)
    public List<ConsultationMessageResponseDTO> getMessagesBySessionId(Long sessionId){
        LambdaQueryWrapper<ConsultationMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConsultationMessage::getSessionId,sessionId)
                .orderByAsc(ConsultationMessage::getCreatedAt)
                .orderByAsc(ConsultationMessage::getId);
        List<ConsultationMessage> messages = consultationMessageMapper.selectList(wrapper);
        return messages.stream().map(this::convertToResponseDTO).collect(Collectors.toList());
    }

    //批量查询多个会话的消息(只查聚合需要的字段,供会话列表统计消息数/最后一条消息)
    public List<ConsultationMessage> getMessagesBySessionIds(List<Long> sessionIds){
        if(sessionIds == null || sessionIds.isEmpty()){
            return new ArrayList<>();
        }
        LambdaQueryWrapper<ConsultationMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(ConsultationMessage::getSessionId, sessionIds);
        return consultationMessageMapper.selectList(wrapper);
    }

    //删除会话下的全部消息
    public void deleteBySessionId(Long sessionId){
        LambdaQueryWrapper<ConsultationMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConsultationMessage::getSessionId,sessionId);
        consultationMessageMapper.delete(wrapper);
    }

    //获取会话最后一条消息
public ConsultationMessageResponseDTO getLastMessageBySessionId(Long sessionId){
    LambdaQueryWrapper<ConsultationMessage> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(ConsultationMessage::getSessionId,sessionId)
            .orderByDesc(ConsultationMessage::getCreatedAt)
            .last("limit 1");
    ConsultationMessage consultationMessage = consultationMessageMapper.selectOne(wrapper);
    return consultationMessage !=null ? convertToResponseDTO(consultationMessage): null;
}

     public ConsultationMessageResponseDTO convertToResponseDTO(ConsultationMessage message) {
        if (message == null) {
            return null;
        }

        // 手动逐字段赋值，确保转换的准确性和可控性
        ConsultationMessageResponseDTO responseDTO = new ConsultationMessageResponseDTO();
        responseDTO.setId(message.getId());
        responseDTO.setSessionId(message.getSessionId());
        responseDTO.setSenderType(message.getSenderType());
        responseDTO.setMessageType(message.getMessageType());
        responseDTO.setContent(message.getContent());
        responseDTO.setEmotionTag(message.getEmotionTag());
        responseDTO.setAiModel(message.getAiModel());
        responseDTO.setCreatedAt(message.getCreatedAt());

        // 设置描述字段（通过实体方法获取）
        responseDTO.setSenderTypeDesc(message.getSenderTypeDesc());
        responseDTO.setMessageTypeDesc(message.getMessageTypeDesc());

        // 计算消息长度
        responseDTO.calculateContentLength();

        return responseDTO;
    }
}
