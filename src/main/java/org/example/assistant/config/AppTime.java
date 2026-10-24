package org.example.assistant.config;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 统一时间入口，消除「依赖系统默认时区」的隐患。
 *
 * <p>此前各实体与控制器直接调用 {@code LocalDateTime.now()} / {@code LocalDate.now()}，
 * 取值随 JVM 所在机器时区变化，多时区部署或跨区迁移时会导致时间戳、按天聚合的趋势图错乱。
 * 这里显式锁定业务时区（默认 Asia/Shanghai，可用环境变量 {@code APP_TIME_ZONE} 覆盖），
 * 所有取当前时间的地方统一走本类，保证行为可预期。
 */
public final class AppTime {

    private static final ZoneId ZONE = ZoneId.of(
            System.getenv().getOrDefault("APP_TIME_ZONE", "Asia/Shanghai"));

    private AppTime() {
    }

    /** 当前业务时区的日期时间（用于落库时间戳） */
    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }

    /** 当前业务时区的日期（用于按天聚合 / 展示） */
    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    public static ZoneId zone() {
        return ZONE;
    }
}
