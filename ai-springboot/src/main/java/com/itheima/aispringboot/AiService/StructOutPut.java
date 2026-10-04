package com.itheima.aispringboot.AiService;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class StructOutPut {
    public record StreamChatSession(
            String sessionId,
            Long userHash,
            String initialMessage,
            Long startTime,
            Long expiryTime,
            String status,
            Integer messageCount
    ){

    }
//Jackson 反序列化时，如果 AI 返回 JSON 里有额外未定义字段，不会抛异常，直接忽略，防止大模型返回额外字段导致解析报错。
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EmotionalAnalysis(
            String primaryEmotion,
            Integer emotionScore,
            //如果不加注解，Jackson 对`isXXX`布尔会有命名歧义，所以显式指定
            @JsonProperty("isNegative") Boolean isNegative,
            Integer riskLevel,
            String riskDescription,
            String suggestion,
            List<String> improvementSuggestions
    ) {
        //解析失败哨兵,用于区分"合法中性情绪"与"解析失败"
        public static final EmotionalAnalysis EMPTY = new EmotionalAnalysis(null, null, null, null, null, null, null);

        public static EmotionalAnalysis neutral() {
            return new EmotionalAnalysis("中性", 50, false, 0,
                    null, "情绪状态平稳", List.of());
        }
    }
}
