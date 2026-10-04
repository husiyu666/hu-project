package com.itheima.aispringboot.controller;

import cn.hutool.json.JSONUtil;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.itheima.aispringboot.AiService.PsychologicalSupportService;
import com.itheima.aispringboot.AiService.StructOutPut;
import com.itheima.aispringboot.DTO.command.ConsultationSessionCreateDTO;
import com.itheima.aispringboot.DTO.command.ConsultationStreamDTO;
import com.itheima.aispringboot.DTO.response.ConsultationMessageResponseDTO;
import com.itheima.aispringboot.DTO.response.ConsultationSessionResponseDTO;
import com.itheima.aispringboot.common.Result;
import com.itheima.aispringboot.common.ResultCode;
import com.itheima.aispringboot.service.ConsultationMessageService;
import com.itheima.aispringboot.service.ConsultationsessionService;
import com.itheima.aispringboot.util.JwtTokenUtil;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/psychological-chat")
public class PsychologicalChat {
    @Autowired
    private PsychologicalSupportService psychologicalSupportService;

    @Autowired
    private ConsultationsessionService consultationsessionService;

    @Autowired
    private ConsultationMessageService consultationMessageService;

    //分页查询当前用户的历史会话列表
    @GetMapping("/sessions")
    public Result<Page<ConsultationSessionResponseDTO>> getHistorySession(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String emotionTag){
        String token = JwtTokenUtil.getCurrentToken();
        DecodedJWT decodedJWT = JwtTokenUtil.verifyToken(token);
        Long userId = decodedJWT.getClaim("userId").asLong();
        Page<ConsultationSessionResponseDTO> page = consultationsessionService.getSessionPage(userId, pageNum, pageSize, emotionTag);
        return Result.ok(page);
    }

    //查询会话的完整对话记录
    @GetMapping("/sessions/{sessionId}/messages")
    public Result<List<ConsultationMessageResponseDTO>> getSessionMessages(@PathVariable Long sessionId){
        String token = JwtTokenUtil.getCurrentToken();
        DecodedJWT decodedJWT = JwtTokenUtil.verifyToken(token);
        Long userId = decodedJWT.getClaim("userId").asLong();
        //校验会话归属,防止越权查看他人会话
        consultationsessionService.getOwnedSession(userId, sessionId);
        return Result.ok(consultationMessageService.getMessagesBySessionId(sessionId));
    }

    //删除会话(连同会话下的全部消息)
    @DeleteMapping("/sessions/{sessionId}")
    public Result<?> deleteSession(@PathVariable Long sessionId){
        String token = JwtTokenUtil.getCurrentToken();
        DecodedJWT decodedJWT = JwtTokenUtil.verifyToken(token);
        Long userId = decodedJWT.getClaim("userId").asLong();
        consultationsessionService.deleteSession(userId, sessionId);
        return Result.ok();
    }

    //查询会话的情绪分析结果(前端情绪花园);refresh=true时强制重新AI分析,否则复用已保存的结果
    @GetMapping("/session/{sessionId}/emotion")
    public Result<StructOutPut.EmotionalAnalysis> getSessionEmotion(@PathVariable String sessionId,
            @RequestParam(value = "refresh", required = false, defaultValue = "false") boolean refresh){
        String token = JwtTokenUtil.getCurrentToken();
        DecodedJWT decodedJWT = JwtTokenUtil.verifyToken(token);
        Long userId = decodedJWT.getClaim("userId").asLong();
        return Result.ok(psychologicalSupportService.analyzeEmotion(userId, sessionId, refresh));
    }

    @PostMapping("/session/start")
    public Result<StructOutPut.StreamChatSession> startSession(@Valid @RequestBody ConsultationSessionCreateDTO createDTO) {
        String token = JwtTokenUtil.getCurrentToken();
        DecodedJWT decodedJWT = JwtTokenUtil.verifyToken(token);
        Long userId = decodedJWT.getClaim("userId").asLong();
        StructOutPut.StreamChatSession session = psychologicalSupportService.startsession(userId, createDTO);
        return Result.ok(session);
    }


    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamChat(@Valid @RequestBody ConsultationStreamDTO streamDTO) {
        //获取当前用户
        String token = JwtTokenUtil.getCurrentToken();
        DecodedJWT decodedJWT = JwtTokenUtil.verifyToken(token);
        Long userId = decodedJWT.getClaim("userId").asLong();

        if (userId == null) {
            return Flux.just(ServerSentEvent.<String>builder()
                    .event("error")
                    .data(JSONUtil.toJsonStr(Result.error(ResultCode.UNAUTHORIZED.getCode(), ResultCode.UNAUTHORIZED.getMsg(), "用户未登录")))
                    .build()
            );
        }
        //开始流式对话
        return psychologicalSupportService.streamPsychologicalChat(streamDTO.getSessionId(), streamDTO.getUserMessage())
                .map(Fragment -> {
                   return ServerSentEvent.<String>builder()
                            .event("message")
                            .data(JSONUtil.toJsonStr(Result.ok(Map.of("content", Fragment, "type", "normal"))))
                            .build();
                })
                .concatWith(Flux.just(ServerSentEvent.<String>builder()
                        .event("done")
                        .data("{}")
                        .build()
                ))
                .delayElements(Duration.ofMillis(50));

    }
}

