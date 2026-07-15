package ch.finyo.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Enables the background schedulers — the market-data syncs and the document sync.
 *
 * Scheduling is switched on only when there is something to schedule, so that no test context
 * spins up a scheduler it has no use for. Either flag turns it on: the price/snapshot jobs run
 * under {@code finyo.sync.enabled}, the SharePoint document sync under {@code finyo.graph.enabled}.
 * The individual {@code @Scheduled} beans carry their own condition, so enabling one family does
 * not start the other.
 */
@Configuration
@EnableScheduling
@ConditionalOnExpression("${finyo.sync.enabled:true} or ${finyo.graph.enabled:false}")
public class SchedulingConfig {

    /**
     * Spring's default scheduler is <b>single-threaded</b>. With one thread, a job that hangs on a
     * vendor does not merely run late — it silently prevents every other job from ever running
     * again. Two threads is enough for the jobs that exist (prices, snapshots, document sync) and
     * removes that failure mode.
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
