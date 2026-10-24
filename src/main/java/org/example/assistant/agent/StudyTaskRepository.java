package org.example.assistant.agent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface StudyTaskRepository extends JpaRepository<StudyTask, Long> {

    List<StudyTask> findByUserIdOrderByPlanDateDescIdDesc(String userId);

    List<StudyTask> findByUserIdAndPlanDateOrderByCreatedAtDesc(String userId, LocalDate planDate);

    List<StudyTask> findByUserIdAndStatusOrderByPlanDateAsc(String userId, String status);
}
