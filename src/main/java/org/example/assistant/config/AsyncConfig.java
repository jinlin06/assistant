package org.example.assistant.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步线程池配置。
 *
 * <p>@EnableAsync 若不显式提供 Executor，Spring 会退回 SimpleAsyncTaskExecutor，
 * 每次调用都新建一条线程、用完即弃，高并发下线程数失控、耗尽资源。
 * 这里提供有界的 ThreadPoolTaskExecutor：固定核心线程 + 上限线程 + 有界队列 +
 * CallerRuns 拒绝策略（队列打满时由调用线程执行，起到背压而非直接丢弃任务）。
 *
 * <p>bean 名 taskExecutor 与 Spring 默认查找名一致，@Async 方法自动使用。
 */
@Configuration
public class AsyncConfig {

    @Bean
    public ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(200);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("assistant-async-");
        executor.setRejectedExecutionHandler(rejectPolicy());
        // 优雅停机：等待正在执行的学情提取任务跑完
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    private RejectedExecutionHandler rejectPolicy() {
        return new ThreadPoolExecutor.CallerRunsPolicy();
    }
}
