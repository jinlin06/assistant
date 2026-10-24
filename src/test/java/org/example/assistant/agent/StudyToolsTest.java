package org.example.assistant.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ToolContext;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * StudyTools 单元测试：验证 Agent 工具在有无用户身份、日期格式、任务匹配等分支下的行为，
 * 重点确保 userId 只从 ToolContext 取、绝不越权操作他人任务。
 */
@ExtendWith(MockitoExtension.class)
class StudyToolsTest {

    @Mock
    private StudyTaskRepository repository;

    private StudyTools studyTools;

    @BeforeEach
    void setUp() {
        studyTools = new StudyTools(repository);
    }

    private ToolContext contextWithUser(String userId) {
        return new ToolContext(userId == null ? Map.of() : Map.of(StudyTools.USER_ID_KEY, userId));
    }

    @Test
    void createTask_withoutUserId_failsAndPersistsNothing() {
        String result = studyTools.createStudyTask("复习 RAG", "2026-10-25", contextWithUser(null));

        assertThat(result).contains("无法识别当前用户身份");
        verify(repository, never()).save(any());
    }

    @Test
    void createTask_invalidDateFormat_returnsHint() {
        String result = studyTools.createStudyTask("复习 RAG", "25-10-2026", contextWithUser("u1"));

        assertThat(result).contains("yyyy-MM-dd");
        verify(repository, never()).save(any());
    }

    @Test
    void createTask_valid_savesWithUserIdFromContext() {
        String result = studyTools.createStudyTask("复习 RAG", "2026-10-25", contextWithUser("u1"));

        assertThat(result).contains("已创建");
        ArgumentCaptor<StudyTask> captor = ArgumentCaptor.forClass(StudyTask.class);
        verify(repository).save(captor.capture());
        StudyTask saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo("u1");
        assertThat(saved.getContent()).isEqualTo("复习 RAG");
        assertThat(saved.getPlanDate()).isEqualTo(LocalDate.of(2026, 10, 25));
    }

    @Test
    void queryPlan_emptyDate_queriesPendingScope() {
        StudyTask task = new StudyTask();
        task.setContent("复习记忆机制");
        task.setStatus("PENDING");
        task.setPlanDate(LocalDate.of(2026, 10, 25));
        when(repository.findByUserIdAndStatusOrderByPlanDateAsc("u1", "PENDING"))
                .thenReturn(List.of(task));

        String result = studyTools.queryStudyPlan("", contextWithUser("u1"));

        assertThat(result).contains("全部未完成").contains("复习记忆机制");
    }

    @Test
    void completeTask_noMatch_returnsNotFound() {
        when(repository.findByUserIdAndStatusOrderByPlanDateAsc("u1", "PENDING"))
                .thenReturn(List.of());

        String result = studyTools.completeStudyTask("不存在的关键词", contextWithUser("u1"));

        assertThat(result).contains("未找到");
        verify(repository, never()).save(any());
    }

    @Test
    void completeTask_match_marksDoneAndPersists() {
        StudyTask task = new StudyTask();
        task.setContent("复习 Spring AI 记忆机制");
        task.setStatus("PENDING");
        task.setPlanDate(LocalDate.of(2026, 10, 25));
        when(repository.findByUserIdAndStatusOrderByPlanDateAsc("u1", "PENDING"))
                .thenReturn(List.of(task));

        String result = studyTools.completeStudyTask("Spring AI", contextWithUser("u1"));

        assertThat(result).contains("任务已完成");
        assertThat(task.getStatus()).isEqualTo("DONE");
        verify(repository).save(task);
    }
}
