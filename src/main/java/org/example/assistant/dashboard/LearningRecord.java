package org.example.assistant.dashboard;

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
 * 学习记录：由大模型从对话中结构化提取（.entity()）后落库，
 * 是前端「学习雷达图」「学习概览」的数据来源。
 */
@Entity
@Table(name = "learning_record", indexes = {
        @Index(name = "idx_user_id", columnList = "user_id")
})
public class LearningRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "learning_record_seq")
    @SequenceGenerator(name = "learning_record_seq", sequenceName = "learning_record_id_seq", allocationSize = 1)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    /** 学习主题，如「Spring AI」「RAG」「MySQL 索引」 */
    @Column(nullable = false, length = 128)
    private String topic;

    /** 掌握程度 0-100，由模型根据对话内容评估 */
    @Column(nullable = false)
    private Integer mastery;

    /** 本次预估学习耗时（分钟） */
    @Column(name = "time_spent_minutes", nullable = false)
    private Integer timeSpentMinutes;

    /** 模型给出的一句话学情点评 */
    @Column(length = 512)
    private String comment;

    @Column(name = "created_at", nullable = false)

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public Integer getMastery() {
        return mastery;
    }

    public void setMastery(Integer mastery) {
        this.mastery = mastery;
    }

    public Integer getTimeSpentMinutes() {
        return timeSpentMinutes;
    }

    public void setTimeSpentMinutes(Integer timeSpentMinutes) {
        this.timeSpentMinutes = timeSpentMinutes;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
