package org.example.assistant.dashboard;

import org.example.assistant.memory.ChatMessageJpaRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 学习看板数据接口：Vue 3 + ECharts 通过本 API 拉取数据，
 * 渲染个性化学习雷达图、AI 使用趋势折线图与概览卡片。
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final LearningRecordRepository learningRecordRepository;
    private final ChatMessageJpaRepository chatMessageJpaRepository;

    public DashboardController(LearningRecordRepository learningRecordRepository,
                               ChatMessageJpaRepository chatMessageJpaRepository) {
        this.learningRecordRepository = learningRecordRepository;
        this.chatMessageJpaRepository = chatMessageJpaRepository;
    }

    /**
     * 学习雷达图数据：ECharts radar 的 indicator + value。
     * 同一主题多轮对话的掌握度取平均值。
     */
    @GetMapping("/radar/{userId}")
    public Map<String, Object> radar(@PathVariable String userId) {
        List<LearningRecordRepository.TopicAgg> aggs =
                learningRecordRepository.aggregateRadarByUser(userId);
        List<Map<String, String>> indicators = aggs.stream()
                .limit(8)
                .map(a -> Map.of("name", a.getTopic(), "max", "100"))
                .toList();
        List<Double> values = aggs.stream()
                .limit(8)
                .map(a -> (double) Math.round(a.getMastery()))
                .toList();
        return Map.of("indicators", indicators, "values", values);
    }

    /**
     */
        Map<LocalDate, Long> counts = new TreeMap<>();
                .forEach(m -> counts.merge(m.getCreatedAt().toLocalDate(), 1L, Long::sum));
        return counts.entrySet().stream()
                .map(e -> Map.<String, Object>of("date", e.getKey().toString(), "count", e.getValue()))
                .toList();
    }

    /**
     * 私人定制概览卡片：总学时、涉及主题数、平均掌握度、最近学情点评。
     */
    @GetMapping("/overview/{userId}")
    public Map<String, Object> overview(@PathVariable String userId) {
        List<LearningRecordRepository.TopicAgg> aggs =
                learningRecordRepository.aggregateRadarByUser(userId);
        long totalMinutes = aggs.stream()
                .mapToLong(a -> a.getMinutes() == null ? 0 : a.getMinutes())
                .sum();
        double avgMastery = aggs.stream()
                .mapToDouble(LearningRecordRepository.TopicAgg::getMastery)
                .average()
                .orElse(0);
        List<LearningRecord> latest = learningRecordRepository.findByUserIdOrderByCreatedAtDesc(userId);
        String lastComment = latest.isEmpty() ? "还没有学习记录，快和 AI 聊聊吧" : latest.get(0).getComment();
        return Map.of(
                "totalMinutes", totalMinutes,
                "topicCount", aggs.size(),
                "avgMastery", Math.round(avgMastery),
                "lastComment", lastComment == null ? "" : lastComment
        );
    }

    /** 最近的学习明细（表格展示用） */
    @GetMapping("/records/{userId}")
    public List<LearningRecord> records(@PathVariable String userId) {
        return learningRecordRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .limit(20)
                .toList();
    }
}
