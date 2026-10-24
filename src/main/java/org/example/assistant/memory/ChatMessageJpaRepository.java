package org.example.assistant.memory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface ChatMessageJpaRepository extends JpaRepository<ChatMessageEntity, Long> {

    List<ChatMessageEntity> findByConversationIdOrderByCreatedAtAscIdAsc(String conversationId);

    List<ChatMessageEntity> findByConversationId(String conversationId);

    /** 按会话（= userId）+ 角色 + 起始时间拉取，保证趋势图按用户隔离统计 */
    List<ChatMessageEntity> findByConversationIdAndRoleAndCreatedAtGreaterThanEqual(
            String conversationId, String role, LocalDateTime since);

    /** 删除指定会话的全部消息 */
    @Transactional
    void deleteByConversationId(String conversationId);
}
