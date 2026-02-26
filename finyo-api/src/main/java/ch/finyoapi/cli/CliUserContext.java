package ch.finyoapi.cli;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Provides the current user ID for CLI commands.
 *
 * <p>In the MVP, the user ID is read from application configuration via the
 * {@code finyo.cli.user-id} property (defaults to the {@code CLI_USER_ID}
 * environment variable). This lets a developer or operator run the shell
 * against any provisioned user without modifying source code.
 *
 * <p>Production note: a future iteration should replace this with an
 * OAuth2 client-credentials flow that performs a Keycloak token exchange on
 * shell startup and stores the resolved subject claim here.
 */
@Component
@RequiredArgsConstructor
public class CliUserContext {

    private final CliProperties cliProperties;

    public String getUserId() {
        return cliProperties.userId();
    }
}
