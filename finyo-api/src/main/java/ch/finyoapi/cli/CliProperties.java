package ch.finyoapi.cli;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "finyo.cli")
public record CliProperties(
        String userId
) {}
