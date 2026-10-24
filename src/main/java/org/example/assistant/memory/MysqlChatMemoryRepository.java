package org.example.assistant.memory;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 基于 MySQL 的自定义 ChatMemoryRepository。
 *
 * <p>摒弃 Spring AI 默认的内存实现（InMemoryChatMemoryRepository），
 * 将对话上下文落库，做到：
 * <ul>
 *   <li>服务重启后记忆不丢失，多实例部署共享上下文；</li>
 *   <li>以 conversationId（= userId）为分区键，不同用户记忆绝对隔离；</li>
 *   <li>配合 {@link org.springframework.ai.chat.memory.MessageWindowChatMemory} 的
 *       滑动窗口策略（最近 20 条）防止 Token 超限。</li>
 * </ul>
 */
@Component
public class MysqlChatMemoryRepository implements ChatMemoryRepository {

    private final ChatMessageJpaRepository jpaRepository;

    public MysqlChatMemoryRepository(ChatMessageJpaRepository jpaRepository) {
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
     * 注意：ChatMemory 接口语义是「全量覆盖保存」。
     * MessageWindowChatMemory 每次传入的已是窗口裁剪后的完整列表，
     * 因此这里先删后插即可保证数据库中只保留最近 N 条上下文。
     */
    @Override
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
