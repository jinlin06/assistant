package org.example.assistant.memory;

import org.example.assistant.config.AppTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 长期记忆摘要服务：把被滑动窗口裁剪掉的早期对话，交给大模型压缩成一段长期摘要并落库。
 *
 * <p>设计要点与学情分析一致：
 * <ul>
 *   <li>复用 analyticsChatClient（无记忆 Advisor），摘要指令与其结果不会污染用户对话记忆；</li>
 *   <li>@Async 异步执行，压缩过程不阻塞用户对话响应；</li>
 *   <li>失败只记日志、绝不抛出影响主对话链路（摘要是旁路增强）。</li>
 * </ul>
 */
@Service
public class ConversationSummaryService {

    private static final Logger log = LoggerFactory.getLogger(ConversationSummaryService.class);

    private static final String SUMMARY_PROMPT = """
            你是对话记忆压缩器。请把「已有摘要」与「被裁剪的较早对话」合并成一段简洁的长期记忆摘要，
            保留用户身份事实、偏好、正在学习的主题、关键结论与未完成事项，忽略寒暄与冗余。
            用简体中文，不超过 300 字，只输出摘要正文本身。
            【已有摘要】
            %s
            【被裁剪的较早对话】
            %s
            """;

    /** 摘要落库长度上限，防止长期摘要无限膨胀反噬上下文 Token */
    private static final int MAX_SUMMARY_CHARS = 1200;

    private final ChatClient analyticsChatClient;
    private final ChatMemorySummaryRepository summaryRepository;

    public ConversationSummaryService(ChatClient analyticsChatClient,
                                      ChatMemorySummaryRepository summaryRepository) {
        this.analyticsChatClient = analyticsChatClient;
        this.summaryRepository = summaryRepository;
    }

    /** 将本轮被裁剪掉的旧消息合并进长期摘要（异步、旁路、失败静默降级） */
    @Async
    public void summarizeAndMerge(String conversationId, List<Message> evicted) {
        if (evicted == null || evicted.isEmpty()) {
            return;
        }
        try {
            String oldSummary = summaryRepository.findById(conversationId)
                    .map(ChatMemorySummary::getContent)
                    .orElse("（无）");
            String merged = analyticsChatClient.prompt()
                    .user(String.format(SUMMARY_PROMPT, oldSummary, render(evicted)))
                    .call()
                    .content();
            if (merged == null || merged.isBlank()) {
                return;
            }
            ChatMemorySummary entity = summaryRepository.findById(conversationId)
                    .orElseGet(() -> {
                        ChatMemorySummary created = new ChatMemorySummary();
                        created.setConversationId(conversationId);
                        return created;
                    });
            entity.setContent(truncate(merged.strip(), MAX_SUMMARY_CHARS));
            entity.setUpdatedAt(AppTime.now());
            summaryRepository.save(entity);
            log.info("长期记忆摘要更新 conversationId={} 字数={}", conversationId, entity.getContent().length());
        } catch (Exception e) {
            log.warn("长期记忆摘要更新失败 conversationId={}: {}", conversationId, e.getMessage());
        }
    }

    private String render(List<Message> messages) {
        StringBuilder sb = new StringBuilder();
        for (Message m : messages) {
            sb.append(m.getMessageType().getValue()).append(": ").append(m.getText()).append('\n');
        }
        return sb.toString();
    }

    private String truncate(String text, int maxLen) {
        return text.length() <= maxLen ? text : text.substring(0, maxLen);
    }
}
