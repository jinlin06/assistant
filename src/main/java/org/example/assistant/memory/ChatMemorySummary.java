package org.example.assistant.memory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.example.assistant.config.AppTime;

import java.time.LocalDateTime;

/**
 * 会话级「长期记忆摘要」实体（PostgreSQL）。
 *
 * <p>滑动窗口只保留最近若干条消息，早期信息会被裁剪丢弃；这里为每个会话
 * （conversationId = userId）持久化一段被大模型压缩后的长期摘要，
 * 使早期关键信息（用户事实、偏好、正在学的主题、未完成事项）得以长期保留。
 *
 * <p>以 conversationId 作为主键，天然保证「一会话一条摘要、随取随更新」，
 * 且无需序列生成主键（PostgreSQL 9.3 友好）。
 */
@Entity
@Table(name = "chat_memory_summary")
public class ChatMemorySummary {

    @Id
    @Column(name = "conversation_id", length = 64)
    private String conversationId;

    /** 压缩后的长期记忆摘要正文 */
    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = AppTime.now();

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
