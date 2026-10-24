package org.example.assistant.dashboard;

/**
 * 大模型结构化输出的载体类。
 * 通过 ChatClient 的 .entity(LearningExtraction.class) 让 GLM 直接返回
 * 符合该 JavaBean 的 JSON 并自动反序列化，无需手工解析。
 */
public class LearningExtraction {

    /** 本轮对话涉及的学习主题，如「Spring AI 记忆机制」 */
    private String topic;

    /** 模型评估的用户掌握程度（0-100） */
    private Integer mastery;

    /** 本轮预估学习耗时（分钟） */
    private Integer timeSpentMinutes;

    /** 一句话学情点评 */
    private String comment;

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
}
