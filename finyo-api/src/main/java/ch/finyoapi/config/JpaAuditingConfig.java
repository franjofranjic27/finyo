package ch.finyoapi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.OffsetDateTime;
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
        return () -> Optional.of(OffsetDateTime.now());
    }
}
