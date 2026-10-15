package com.husiyu.aipsy.AiService;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.husiyu.aipsy.DTO.command.ConsultationSessionCreateDTO;
import com.husiyu.aipsy.DTO.response.ConsultationMessageResponseDTO;
import com.husiyu.aipsy.DTO.response.ConsultationSessionResponseDTO;
import com.husiyu.aipsy.controller.User;
import com.husiyu.aipsy.entity.ConsultationMessage;
import com.husiyu.aipsy.entity.ConsultationSession;
import com.husiyu.aipsy.exception.BusinessException;
import com.husiyu.aipsy.service.ConsultationMessageService;
import com.husiyu.aipsy.service.ConsultationsessionService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.View;
import reactor.core.publisher.Flux;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PsychologicalSupportService {

    @Autowired
    @Qualifier("open-ai")
    private ChatClient chatClient;
    @Autowired
    private ConsultationsessionService consultationsessionService;

    //情绪分析专用配置(与聊天流共享同一个client时,并发请求可能因连接复用而串包,故独立连接)
    @Value("${spring.ai.openai.base-url}")
    private String openAiBaseUrl;
    @Value("${spring.ai.openai.api-key}")
    private String openAiApiKey;
    @Value("${spring.ai.openai.chat.completions-path}")
    private String openAiCompletionsPath;
    private volatile ChatClient emotionClient;

    //懒加载构建情绪分析专用ChatClient:独立HttpClient连接,避免与流式聊天并发时响应串包
    private ChatClient emotionClient(){
        if(emotionClient == null){
            synchronized(this){
                if(emotionClient == null){
                    OpenAiApi api = OpenAiApi.builder()
                            .baseUrl(openAiBaseUrl)
                            .apiKey(openAiApiKey)
                            .completionsPath(openAiCompletionsPath)
                            .restClientBuilder(RestClient.builder()
                                    .requestFactory(new JdkClientHttpRequestFactory(HttpClient.newHttpClient())))
                            .build();
                    OpenAiChatModel model = OpenAiChatModel.builder()
                            .openAiApi(api)
                            .defaultOptions(org.springframework.ai.openai.OpenAiChatOptions.builder()
                                    .model("deepseek-ai/DeepSeek-V3")
                                    .build())
                            .build();
                    emotionClient = ChatClient.builder(model).build();
                }
            }
        }
        return emotionClient;
    }

    @Autowired
    private ConsultationMessageService consultationMessageService;
    @Autowired
    private User user;
    @Qualifier("chatMemory")
    @Autowired
    private ChatMemory chatMemory;
    @Autowired
    private View error;

//    public ConsultationSessionResponseDTO getHistorySession(String emotionTag){
//
//    }

    public StructOutPut.StreamChatSession startsession(Long userId, ConsultationSessionCreateDTO createDTO){
    //创建数据库会话记录
    ConsultationSession session = consultationsessionService.createSession(userId, createDTO);

    //将初始用户消息保存到Message表中
    ConsultationMessage consultationMessage = consultationMessageService.saveUserMessage(session.getId(), createDTO.getInitialMessage(), null);
    System.out.println("Message数据："+consultationMessage);

    //创建会话信息
   String sessionId= "session_"+session.getId();
    return new StructOutPut.StreamChatSession(
            sessionId,
            userId,
            createDTO.getInitialMessage(),
            System.currentTimeMillis(),
            System.currentTimeMillis()+86400000L,
            "ACTIVE",
            1
    );
}

//8.19
public Flux<String> streamPsychologicalChat(String sessionId,String userMessage){
    return Flux.create(sink -> {
       //sink.next("数据1") //发布数据
       //sink.complete() //完成流
        // sink.error(exception) //发布错误
        Long l = extractSessionId(sessionId);
        if(l==null){
             sink.error(new RuntimeException("会话ID格式错误"));
             return ;
        }

        //检查是否为初始消息，避免重复保存
        Integer messageCountBysessionId = consultationMessageService.getMessageCountBysessionId(l);
        //是否为初始消息
        boolean isInitialMessage=false;
        if(messageCountBysessionId==1){
            ConsultationMessageResponseDTO lastMessageBySessionId = consultationMessageService.getLastMessageBySessionId(l);
            if(lastMessageBySessionId!=null && lastMessageBySessionId.getSenderType()==1 && userMessage.equals(lastMessageBySessionId.getContent())){
                isInitialMessage=true;
            }
        }
        if(!isInitialMessage){
            //保存用户信息到数据库
            consultationMessageService.saveUserMessage(l,userMessage,null);
        }

         //进行流式对话
        //生成对话记忆管理
        String conversationId=  "conversation_"+sessionId;
        //构建系统提示词
        List<Message>  userMessages = new ArrayList<>();
        userMessages.add(new UserMessage(userMessage));
        chatMemory.add(conversationId,userMessages);
        Prompt prompt=new Prompt(List.of(
                //系统提示词
                new SystemMessage(PromptManage.PSYCHOLOGICAL_SUPPORT_SYSTEM_PROMPT)
        ));

        //用于存储AI完成的响应
        StringBuilder stringBuilder = new StringBuilder();

        //使用chatClient发送消息到Open AI
        chatClient.prompt(prompt)
                .user(userMessage)
                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.DEFAULT_CONVERSATION_ID,conversationId))
                .stream()
                .content()
                .doOnNext(Fragment-> {
                    stringBuilder.append(Fragment);
                    sink.next(Fragment);
                })

                .doOnComplete(()->{
                    String completeRes = stringBuilder.toString();
                    //将AI返回的内容保存到数据库
                    consultationMessageService.saveAimessage(l,completeRes,"openai");
                    //添加AI回复到chatMemory
                    List<Message> aiMessage=new ArrayList<>();
                    aiMessage.add(new AssistantMessage(completeRes));
                    chatMemory.add(conversationId,aiMessage);

                    sink.complete();
                })
                .doOnError(error->{
                    sink.error(error);
                })
                .subscribe();//订阅并启动流
    });
}

public Long extractSessionId(String sessionId){
    if(sessionId!=null && sessionId.startsWith("session_")){
        String substring = sessionId.substring("session_".length());
         return Long.parseLong((substring));
    }
    return null;

}

//9.16 情绪分析:读取会话对话记录,使用AI分析情绪状态;refresh=false时优先复用已保存结果,加快切换会话速度
public StructOutPut.EmotionalAnalysis analyzeEmotion(Long userId, String rawSessionId, boolean refresh){
    Long sessionId;
    try {
        if(rawSessionId != null && rawSessionId.startsWith("session_")){
            sessionId = Long.parseLong(rawSessionId.substring("session_".length()));
        } else {
            sessionId = Long.parseLong(rawSessionId);
        }
    } catch (Exception e) {
        throw new BusinessException("会话ID格式错误");
    }
    //校验会话归属,防止越权查看他人会话
    ConsultationSession session = consultationsessionService.getOwnedSession(userId, sessionId);
    //非强制刷新:优先复用已保存结果;没有结果则直接返回中性,不调用AI(避免切换会话时并发分析)
    if(!refresh){
        if(session.getLastEmotionAnalysis() != null && !session.getLastEmotionAnalysis().isBlank()){
            StructOutPut.EmotionalAnalysis cached = parseEmotion(session.getLastEmotionAnalysis());
            if(cached != StructOutPut.EmotionalAnalysis.EMPTY){
                return cached;
            }
        }
        return StructOutPut.EmotionalAnalysis.neutral();
    }
    //读取会话全部消息
    List<ConsultationMessageResponseDTO> messages = consultationMessageService.getMessagesBySessionId(sessionId);
    //无对话内容时返回默认情绪
    if(messages == null || messages.isEmpty()){
        return StructOutPut.EmotionalAnalysis.neutral();
    }
    //拼接对话记录(最多取最近20条,控制输入长度)
    String dialog = messages.stream()
            .limit(20)
            .map(m -> ((m.getSenderType() != null && m.getSenderType() == 1) ? "用户" : "助手") + "：" + m.getContent())
            .collect(Collectors.joining("\n"));

    String prompt = PromptManage.EMOTION_ANALYSIS_PROMPT + "\n【对话记录】\n" + dialog;

    //AI调用失败时直接返回默认情绪,避免异常冒泡导致接口返回无意义的403
    String content;
    try {
        content = emotionClient().prompt().user(prompt).call().content();
    } catch (Throwable e) {
        logEmotionError(e, "AI调用失败,baseUrl=" + openAiBaseUrl + ",apiKey=" + openAiApiKey + ",path=" + openAiCompletionsPath);
        return StructOutPut.EmotionalAnalysis.neutral();
    }

    //解析模型输出的JSON(模型可能包一层```json代码块,需要提取纯JSON)
    StructOutPut.EmotionalAnalysis analysis = parseEmotion(content);
    //解析失败时回退为默认中性情绪
    if(analysis == StructOutPut.EmotionalAnalysis.EMPTY){
        analysis = StructOutPut.EmotionalAnalysis.neutral();
    }

    //持久化:数据库last_emotion_analysis列类型为json,必须写入规范合法的JSON
    try {
        consultationsessionService.updateEmotionAnalysis(sessionId, toEmotionJson(analysis));
    } catch (Throwable e) {
        System.out.println("情绪分析结果保存失败: " + e.getMessage());
    }
    return analysis;
}

//从模型输出中提取并解析情绪分析JSON,解析失败返回EMPTY哨兵
private StructOutPut.EmotionalAnalysis parseEmotion(String content){
    if(content == null || content.isBlank()){
        return StructOutPut.EmotionalAnalysis.EMPTY;
    }
    String json = content.trim();
    //去掉markdown代码块围栏(```json ... ```等),只保留最外层大括号块
    int start = json.indexOf('{');
    int end = json.lastIndexOf('}');
    if(start < 0 || end <= start){
        return StructOutPut.EmotionalAnalysis.EMPTY;
    }
    json = json.substring(start, end + 1);
    try {
        JSONObject obj = JSONUtil.parseObj(json);
        List<String> improvements = List.of();
        JSONArray arr = obj.getJSONArray("improvementSuggestions");
        if(arr != null){
            improvements = arr.toList(String.class);
        }
        return new StructOutPut.EmotionalAnalysis(
                obj.getStr("primaryEmotion", "中性"),
                obj.getInt("emotionScore", 50),
                obj.getBool("isNegative", false),
                obj.getInt("riskLevel", 0),
                obj.getStr("riskDescription", null),
                obj.getStr("suggestion", "情绪状态平稳"),
                improvements
        );
    } catch (Throwable e) {
        return StructOutPut.EmotionalAnalysis.EMPTY;
    }
}

//把情绪分析结果序列化为规范JSON字符串(数据库列类型为json)
private String toEmotionJson(StructOutPut.EmotionalAnalysis analysis){
    return JSONUtil.createObj()
            .set("primaryEmotion", analysis.primaryEmotion())
            .set("emotionScore", analysis.emotionScore())
            .set("isNegative", analysis.isNegative())
            .set("riskLevel", analysis.riskLevel())
            .set("riskDescription", analysis.riskDescription())
            .set("suggestion", analysis.suggestion())
            .set("improvementSuggestions", analysis.improvementSuggestions())
            .toString();
}

//把异常写入临时日志文件,便于排查(IDEA控制台日志无法直接读取)
private void logEmotionError(Throwable e, String context){
    try {
        java.io.StringWriter sw = new java.io.StringWriter();
        e.printStackTrace(new java.io.PrintWriter(sw));
        String msg = java.time.LocalDateTime.now() + " [" + context + "] " + e.getClass().getName() + ": " + e.getMessage() + "\n" + sw + "----\n";
        java.nio.file.Files.write(
                java.nio.file.Paths.get("C:\\Users\\HuSiyu\\AppData\\Local\\Temp\\opencode\\emotion-err.log"),
                msg.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
    } catch (Exception ignore) {
    }
}
}
