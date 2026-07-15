package ch.finyo.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Enables the background syncs.
 *
 * Off in tests ({@code finyo.sync.enabled=false}), because a scheduled job that fires during an
 * integration test would reach for the network — slow, flaky, and rude to endpoints finyo is only
 * tolerated on.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "finyo.sync", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {

    /**
     * Spring's default scheduler is <b>single-threaded</b>. With one thread, a price sync that
     * hangs on a vendor does not merely run late — it silently prevents every other job from ever
     * running again. Two threads is enough for the jobs that exist and removes that failure mode.
     */
    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("finyo-sync-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        return scheduler;
    }
}
