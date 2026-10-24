package org.example.assistant.memory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 替代默认的内存式 ChatMemoryRepository，实现重启不丢、多实例共享。
 */
@Entity
@Table(name = "chat_message", indexes = {
        @Index(name = "idx_conversation_id", columnList = "conversation_id")
})
public class ChatMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "chat_message_seq")
    @SequenceGenerator(name = "chat_message_seq", sequenceName = "chat_message_id_seq", allocationSize = 1)
    private Long id;

    /** 会话 ID（约定以 userId 作为 conversationId，实现用户级记忆隔离） */
    @Column(name = "conversation_id", nullable = false, length = 64)
    private String conversationId;

    /** 消息角色：user / assistant / system */
    @Column(nullable = false, length = 16)
    private String role;

    /** 消息内容 */
    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "created_at", nullable = false)

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
