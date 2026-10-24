package org.example.assistant.memory;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 长期记忆摘要仓库：以 conversationId 为主键，一会话一条摘要。
 */
public interface ChatMemorySummaryRepository extends JpaRepository<ChatMemorySummary, String> {
}
