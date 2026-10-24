package org.example.assistant.memory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface ChatMessageJpaRepository extends JpaRepository<ChatMessageEntity, Long> {

    List<ChatMessageEntity> findByConversationIdOrderByCreatedAtAscIdAsc(String conversationId);

    List<ChatMessageEntity> findByConversationId(String conversationId);

    /** 按角色 + 起始时间拉取消息，供上层聚合出「AI 使用趋势」折线图（跨数据库可移植） */
    List<ChatMessageEntity> findByRoleAndCreatedAtGreaterThanEqual(String role, LocalDateTime since);

    /** 按会话（= userId）+ 角色 + 起始时间拉取，保证趋势图按用户隔离统计 */
    List<ChatMessageEntity> findByConversationIdAndRoleAndCreatedAtGreaterThanEqual(
            String conversationId, String role, LocalDateTime since);

    /** 删除指定会话的全部消息 */
    @Transactional
    void deleteByConversationId(String conversationId);
}
