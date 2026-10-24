package org.example.assistant.dashboard;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LearningRecordRepository extends JpaRepository<LearningRecord, Long> {

    List<LearningRecord> findByUserIdOrderByCreatedAtDesc(String userId);

    /** 雷达图数据：按主题聚合平均掌握度（同一主题多轮对话取均值） */
    @Query("SELECT r.topic AS topic, AVG(r.mastery) AS mastery, SUM(r.timeSpentMinutes) AS minutes " +
            "FROM LearningRecord r WHERE r.userId = :userId " +
            "GROUP BY r.topic ORDER BY AVG(r.mastery) DESC")
    List<TopicAgg> aggregateRadarByUser(@Param("userId") String userId);

    /** 雷达图投影 */
    interface TopicAgg {
        String getTopic();

        Double getMastery();

        Long getMinutes();
    }
}
