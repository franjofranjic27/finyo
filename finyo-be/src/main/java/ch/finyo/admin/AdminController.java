package ch.finyo.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Administrative endpoints (ROLE_admin required)")
@PreAuthorize("hasRole('admin')")
public class AdminController {

    @GetMapping("/health")
    @Operation(summary = "Admin health check")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "role", "admin"
        ));
    }

    @GetMapping("/users")
    @Operation(summary = "List all users (stub — returns placeholder until user management is implemented)")
    public ResponseEntity<Map<String, Object>> users() {
        return ResponseEntity.ok(Map.of(
            "message", "User management is managed via Keycloak. Visit http://localhost:8081/admin/finyo/console for user administration.",
            "keycloakAdminUrl", "http://localhost:8081/admin/finyo/console"
        ));
    }
}
