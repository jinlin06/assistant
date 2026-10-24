package org.example.assistant.agent;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Agent 工具集：基于 Spring AI Tools 机制，把普通 Java 方法注册为大模型可调用的工具。
 *
 * <p>闭环链路：用户自然语言（如「帮我建个任务，明天复习 RAG」）
 * → GLM 意图识别并生成工具调用参数 → Spring AI 反射执行本类方法 →
 * 结果回填给模型 → 模型组织成自然语言回复。
 *
 * <p>userId 通过 ToolContext 透传（对话时以 conversationId 传入），
 * 不暴露给模型，避免越权操作他人任务。
 */
@Component
public class StudyTools {

    /** 与 ChatController 约定的 ToolContext 键 */
    public static final String USER_ID_KEY = "userId";

    private final StudyTaskRepository studyTaskRepository;

    public StudyTools(StudyTaskRepository studyTaskRepository) {
        this.studyTaskRepository = studyTaskRepository;
    }

    @Tool(description = "创建一条学习任务。当用户希望安排、记录某项学习计划时调用。")
    public String createStudyTask(
            @ToolParam(description = "任务内容，例如：复习 Spring AI 记忆机制") String content,
            @ToolParam(description = "计划完成日期，格式 yyyy-MM-dd") String planDate,
            ToolContext toolContext) {
        String userId = resolveUserId(toolContext);
        if (userId == null) {
            return "创建失败：无法识别当前用户身份。";
        }
        LocalDate date;
        try {
            date = LocalDate.parse(planDate);
        } catch (DateTimeParseException e) {
            return "创建失败：日期格式应为 yyyy-MM-dd，例如 2026-10-25。";
        }
        StudyTask task = new StudyTask();
        task.setUserId(userId);
        task.setContent(content);
        task.setPlanDate(date);
        studyTaskRepository.save(task);
        return "已创建学习任务：%s（计划日期 %s）。".formatted(content, date);
    }

    @Tool(description = "查询学习日程/任务清单。当用户询问今天或最近有哪些安排、待办时调用。")
    public String queryStudyPlan(
            @ToolParam(description = "查询日期，格式 yyyy-MM-dd；查询全部待办时传空字符串") String date,
            ToolContext toolContext) {
        String userId = resolveUserId(toolContext);
        if (userId == null) {
            return "查询失败：无法识别当前用户身份。";
        }
        List<StudyTask> tasks;
        String scope;
        if (date == null || date.isBlank()) {
            tasks = studyTaskRepository.findByUserIdAndStatusOrderByPlanDateAsc(userId, "PENDING");
            scope = "全部未完成";
        } else {
            tasks = studyTaskRepository.findByUserIdAndPlanDateOrderByCreatedAtDesc(
                    userId, LocalDate.parse(date));
            scope = date;
        }
        if (tasks.isEmpty()) {
            return scope + "没有学习任务。";
        }
        return scope + "任务共 " + tasks.size() + " 条：\n" + tasks.stream()
                .map(t -> "- [%s] %s（%s）".formatted(t.getStatus(), t.getContent(), t.getPlanDate()))
                .collect(Collectors.joining("\n"));
    }

    @Tool(description = "将一条学习任务标记为已完成。当用户表示某个任务已经做完时调用。")
    public String completeStudyTask(
            @ToolParam(description = "任务关键词，用于在用户任务列表中匹配") String keyword,
            ToolContext toolContext) {
        String userId = resolveUserId(toolContext);
        if (userId == null) {
            return "操作失败：无法识别当前用户身份。";
        }
        List<StudyTask> matched = studyTaskRepository
                .findByUserIdAndStatusOrderByPlanDateAsc(userId, "PENDING").stream()
                .filter(t -> t.getContent().contains(keyword))
                .toList();
        if (matched.isEmpty()) {
            return "未找到包含「%s」的未完成学习任务。".formatted(keyword);
        }
        StudyTask task = matched.get(0);
        task.setStatus("DONE");
        studyTaskRepository.save(task);
        return "任务已完成：%s（%s）。".formatted(task.getContent(), task.getPlanDate());
    }

    private String resolveUserId(ToolContext toolContext) {
        if (toolContext == null || toolContext.getContext() == null) {
            return null;
        }
        Object value = toolContext.getContext().get(USER_ID_KEY);
        return value == null ? null : value.toString();
    }
}
