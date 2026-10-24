package org.example.assistant.dashboard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 学情分析服务：利用大模型的结构化输出能力，
 * 把每轮「提问 + 回答」交给 GLM 分析，自动提取学习主题、掌握程度、耗时，
 * 落库后供 Vue + ECharts 看板渲染雷达图 / 概览卡片。
 *
 * <p>两点设计：
 * <ul>
 *   <li>使用独立的 analyticsChatClient（无记忆 Advisor），
 *       避免分析指令污染用户的对话记忆；</li>
 *   <li>@Async 异步执行，提取过程不阻塞用户的对话响应。</li>
 * </ul>
 */
@Service
public class LearningAnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(LearningAnalyticsService.class);

    private static final String EXTRACT_PROMPT = """
            请分析以下一轮学习对话，提取学情数据：
            【学生提问】%s
            【AI 回答】%s
            要求：
            1. topic：用简短中文概括本轮涉及的学习主题；
            2. mastery：根据学生提问的 depth 与准确性评估其对该主题的掌握程度（0-100 整数）；
            3. timeSpentMinutes：预估本轮学习该主题花费的分钟数（正整数）；
            4. comment：给出一句鼓励性的学情点评。
            """;

    private final ChatClient analyticsChatClient;
    private final LearningRecordRepository learningRecordRepository;

    public LearningAnalyticsService(ChatClient analyticsChatClient,
                                    LearningRecordRepository learningRecordRepository) {
        this.analyticsChatClient = analyticsChatClient;
        this.learningRecordRepository = learningRecordRepository;
    }

    /** 每轮对话结束后异步提取学情数据并落库 */
    @Async
    public void extractAndSaveAsync(String userId, String question, String answer) {
        try {
            LearningExtraction extraction = analyticsChatClient.prompt()
                    .user(String.format(EXTRACT_PROMPT, question, answer))
                    .call()
                    // 结构化输出：Spring AI 自动追加 JSON Schema 约束并完成反序列化
                    .entity(LearningExtraction.class);

            if (extraction == null || extraction.getTopic() == null) {
                return;
            }
            LearningRecord record = new LearningRecord();
            record.setUserId(userId);
            record.setTopic(extraction.getTopic());
            record.setMastery(clamp(extraction.getMastery(), 0, 100, 60));
            record.setTimeSpentMinutes(clamp(extraction.getTimeSpentMinutes(), 1, 600, 10));
            record.setComment(truncate(extraction.getComment(), 500));
            learningRecordRepository.save(record);
            log.info("学情提取完成 userId={} topic={}", userId, record.getTopic());
        } catch (Exception e) {
            // 看板数据属于旁路分析，失败只记日志，绝不影响主对话链路
            log.warn("学情提取失败 userId={}: {}", userId, e.getMessage());
        }
    }

    private int clamp(Integer value, int min, int max, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        return Math.max(min, Math.min(max, value));
    }

    private String truncate(String text, int maxLen) {
        if (text == null) {
            return null;
        }
        return text.length() <= maxLen ? text : text.substring(0, maxLen);
    }
}
