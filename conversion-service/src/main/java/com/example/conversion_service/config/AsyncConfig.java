package com.example.conversion_service.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Configuration
@EnableAsync
public class AsyncConfig {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    @Bean(name = "buildExecutor")
    public Executor buildExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("electron-build-");

        executor.setRejectedExecutionHandler((r, exec) -> {
            log.error("[buildExecutor] Build task rejected — queue full (active={}, queueSize={}). " +
                      "Consider raising webtodesk.build.queue-capacity or reducing concurrent builds.",
                    exec.getActiveCount(), exec.getQueue().size());
            throw new RejectedExecutionException("Build queue is full — please try again shortly");
        });

        executor.setTaskDecorator((TaskDecorator) runnable -> () -> {
            try {
                runnable.run();
            } catch (Exception e) {
                log.error("[electron-build] Uncaught exception in build thread: {}", e.getMessage(), e);
                throw e;
            }
        });

        executor.initialize();
        return executor;
    }
}
