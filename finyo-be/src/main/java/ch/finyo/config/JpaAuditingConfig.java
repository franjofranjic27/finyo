package ch.finyo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * JPA auditing lives in its own configuration class (not on the main
 * application class) so that web-layer test slices such as @WebMvcTest,
 * which start without a JPA metamodel, can load their context.
 *
 * The DateTimeProvider supplies OffsetDateTime because the entities type
 * their audit columns as OffsetDateTime (timestamptz) — Spring Data's
 * default LocalDateTime cannot be converted to that.
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditingConfig {

    @Bean
    public DateTimeProvider auditingDateTimeProvider() {
        // Auditing timestamps are persisted in UTC (timestamptz columns)
        return () -> Optional.of(OffsetDateTime.now(ZoneOffset.UTC));
    }
}
