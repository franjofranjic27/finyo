package ch.finyo.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Scheduling exists only when there is something to schedule — today that is the
 * document sync. Kept off the application class (like {@link JpaAuditingConfig})
 * so no test context spins up a scheduler it has no use for.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "finyo.graph.enabled", havingValue = "true")
public class SchedulingConfig {
}
