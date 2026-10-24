package org.example.assistant.agent;

import org.springframework.format.annotation.DateTimeFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 学习任务直连接口：前端日程面板的 CRUD；
 * 与 {@link StudyTools} 共享同一份数据，实现「对话建任务、页面看任务」。
 */
@RestController
@Validated
@RequestMapping("/api/tasks")
public class TaskController {

    private final StudyTaskRepository studyTaskRepository;

    public TaskController(StudyTaskRepository studyTaskRepository) {
        this.studyTaskRepository = studyTaskRepository;
    }

    @GetMapping("/{userId}")
    public List<StudyTask> list(@PathVariable String userId,
                                @RequestParam(required = false)
                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        if (date != null) {
            return studyTaskRepository.findByUserIdAndPlanDateOrderByCreatedAtDesc(userId, date);
        }
        return studyTaskRepository.findByUserIdOrderByPlanDateDescIdDesc(userId);
    }

    @PostMapping
    public StudyTask create(@RequestParam @NotBlank @Size(max = 64) String userId,
                            @RequestParam @NotBlank @Size(max = 255) String content,
                            @RequestParam @NotNull
                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate planDate) {
        StudyTask task = new StudyTask();
        task.setUserId(userId);
        task.setContent(content);
        task.setPlanDate(planDate);
        return studyTaskRepository.save(task);
    }

    @PutMapping("/{id}/complete")
    public StudyTask complete(@PathVariable Long id) {
        StudyTask task = studyTaskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + id));
        task.setStatus("DONE");
        return studyTaskRepository.save(task);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        studyTaskRepository.deleteById(id);
    }
}
