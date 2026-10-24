package org.example.assistant.memory;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 基于 PostgreSQL 的自定义 ChatMemoryRepository。
 *
 * <p>摒弃 Spring AI 默认的内存实现（InMemoryChatMemoryRepository），
 * 将对话上下文落库，做到：
 * <ul>
 *   <li>服务重启后记忆不丢失，多实例部署共享上下文；</li>
 *   <li>以 conversationId（= userId）为分区键，不同用户记忆绝对隔离；</li>
 *   <li>配合 {@link SummarizingChatMemory} 的滑动窗口（最近 20 条）+ 长期记忆摘要策略防止 Token 超限。</li>
 * </ul>
 */
@Component
public class PostgresChatMemoryRepository implements ChatMemoryRepository {

    private final ChatMessageJpaRepository jpaRepository;

    public PostgresChatMemoryRepository(ChatMessageJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<String> findConversationIds() {
        return jpaRepository.findAll().stream()
                .map(ChatMessageEntity::getConversationId)
                .distinct()
                .toList();
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        return jpaRepository.findByConversationIdOrderByCreatedAtAscIdAsc(conversationId)
                .stream()
                .map(this::toMessage)
                .toList();
    }

    /**
     * 注意：ChatMemoryRepository 接口语义是「全量覆盖保存」。
     * 上层 {@link SummarizingChatMemory} 每次传入 saveAll 的已是窗口裁剪后的完整列表，
     * 因此这里先删后插即可保证数据库中只保留最近 N 条上下文。
     *
     * <p>删除 + 插入必须处于同一事务：若删完后插入失败回滚，可避免出现
     * 「旧记忆已被清空、新记忆又没写进去」导致该用户记忆整体丢失的情况。
     */
    @Override
    @Transactional
    public void saveAll(String conversationId, List<Message> messages) {
        jpaRepository.deleteByConversationId(conversationId);
        List<ChatMessageEntity> entities = messages.stream()
                .map(m -> toEntity(conversationId, m))
                .toList();
        jpaRepository.saveAll(entities);
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        jpaRepository.deleteByConversationId(conversationId);
    }

    private Message toMessage(ChatMessageEntity entity) {
        MessageType type = MessageType.fromValue(entity.getRole());
        return switch (type) {
            case USER -> new UserMessage(entity.getContent());
            case ASSISTANT -> new AssistantMessage(entity.getContent());
            case SYSTEM -> new SystemMessage(entity.getContent());
            default -> new UserMessage(entity.getContent());
        };
    }

    private ChatMessageEntity toEntity(String conversationId, Message message) {
        ChatMessageEntity entity = new ChatMessageEntity();
        entity.setConversationId(conversationId);
        entity.setRole(message.getMessageType().getValue());
        entity.setContent(message.getText());
        return entity;
    }
}
