package org.example.assistant.memory;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * 带「长期记忆摘要」的滑动窗口 ChatMemory —— 替代 Spring AI 内置的 MessageWindowChatMemory。
 *
 * <p>解决的问题：滑动窗口只留最近 N 条，早期重要信息会被直接丢弃「遗忘」。这里在裁剪旧消息时，
 * 把被丢弃的部分异步交给大模型压缩成一段长期摘要（{@link ConversationSummaryService}）落库，
 * 之后每次读取上下文时，摘要作为一条提示消息重新注入，实现「短期精确 + 长期概要」的兼顾。
 *
 * <p>与 {@code MessageChatMemoryAdvisor} 的协作契约（依据 Spring AI 1.0.0 源码确认）：
 * <ul>
 *   <li>Advisor 在 before() 只写入本轮 user 消息、after() 只写入 assistant 输出——即 {@code add} 收到的始终是「增量」，
 *       因此这里手动把增量追加到窗口再裁剪即可，无需担心重复存储；</li>
 *   <li>{@code get} 返回的摘要消息仅用于本轮上下文注入，Advisor 不会把它回写进 {@code add}，故摘要不会被二次落库。</li>
 * </ul>
 */
public class SummarizingChatMemory implements ChatMemory {

    /** 摘要以「用户消息」形式注入，避免与客户端 defaultSystem 叠加成两条 system 角色 */
    private static final String SUMMARY_PREFIX = "（以下为本次会话较早内容的长期记忆摘要，供参考）\n";

    private final ChatMemoryRepository chatMemoryRepository;
    private final ChatMemorySummaryRepository summaryRepository;
    private final ConversationSummaryService summaryService;
    private final int maxMessages;

    public SummarizingChatMemory(ChatMemoryRepository chatMemoryRepository,
                                 ChatMemorySummaryRepository summaryRepository,
                                 ConversationSummaryService summaryService,
                                 int maxMessages) {
        this.chatMemoryRepository = chatMemoryRepository;
        this.summaryRepository = summaryRepository;
        this.summaryService = summaryService;
        this.maxMessages = maxMessages;
    }

    @Override
    public List<Message> get(String conversationId) {
        List<Message> window = chatMemoryRepository.findByConversationId(conversationId);
        String summary = summaryRepository.findById(conversationId)
                .map(ChatMemorySummary::getContent)
                .orElse(null);
        List<Message> result = new ArrayList<>();
        if (summary != null && !summary.isBlank()) {
            result.add(new UserMessage(SUMMARY_PREFIX + summary));
        }
        result.addAll(window);
        return result;
    }

    /**
     * 追加增量消息并按窗口大小裁剪；超出的旧消息不直接丢弃，而是触发异步压缩进长期摘要。
     * {@code add(String, Message)} 由 {@link ChatMemory} 默认实现转调此方法，无需单独覆写。
     */
    @Override
    public void add(String conversationId, List<Message> messages) {
        List<Message> existing = chatMemoryRepository.findByConversationId(conversationId);
        List<Message> combined = new ArrayList<>(existing);
        combined.addAll(messages);

        if (combined.size() > maxMessages) {
            int overflow = combined.size() - maxMessages;
            List<Message> evicted = List.copyOf(combined.subList(0, overflow));
            List<Message> kept = List.copyOf(combined.subList(overflow, combined.size()));
            chatMemoryRepository.saveAll(conversationId, kept);
            summaryService.summarizeAndMerge(conversationId, evicted);
        } else {
            chatMemoryRepository.saveAll(conversationId, combined);
        }
    }

    @Override
    public void clear(String conversationId) {
        chatMemoryRepository.deleteByConversationId(conversationId);
        if (summaryRepository.existsById(conversationId)) {
            summaryRepository.deleteById(conversationId);
        }
    }
}
