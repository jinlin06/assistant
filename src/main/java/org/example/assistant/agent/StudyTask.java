package org.example.assistant.agent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import org.example.assistant.config.AppTime;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 学习任务：Agent 工具（创建任务 / 查询日程）操作的领域对象。
 */
@Entity
@Table(name = "study_task", indexes = {
        // 高频查询均为组合条件；(user_id, plan_date) 的最左前缀已覆盖单列 user_id 查询，
        // 故不再单建 user_id 索引，避免冗余。
        @Index(name = "idx_task_user_plan", columnList = "user_id, plan_date"),
        @Index(name = "idx_task_user_status_plan", columnList = "user_id, status, plan_date")
})
public class StudyTask {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "study_task_seq")
    @SequenceGenerator(name = "study_task_seq", sequenceName = "study_task_id_seq", allocationSize = 1)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    /** 任务内容，如「刷完 Spring AI 记忆机制章节」 */
    @Column(nullable = false, length = 255)
    private String content;

    /** 计划完成日期 */
    @Column(name = "plan_date", nullable = false)
    private LocalDate planDate;

    /** PENDING / DONE */
    @Column(nullable = false, length = 16)
    private String status = "PENDING";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = AppTime.now();

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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDate getPlanDate() {
        return planDate;
    }

    public void setPlanDate(LocalDate planDate) {
        this.planDate = planDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
