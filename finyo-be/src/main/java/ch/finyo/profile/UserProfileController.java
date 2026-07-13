package ch.finyo.profile;

import ch.finyo.common.UserContextProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
@Tag(name = "Profile", description = "Per-user profile master data, UI preferences and onboarding state")
public class UserProfileController {

    private final UserProfileService userProfileService;
    private final UserContextProvider userContextProvider;

    @GetMapping
    @Operation(summary = "Get the user profile with derived age and retirement figures; "
            + "returns defaults when none is set")
    @ApiResponse(responseCode = "200", description = "User profile returned successfully")
    public ResponseEntity<UserProfileResponse> get() {
        String userId = userContextProvider.getUserId();
        log.info("GET /api/v1/profile user={}", userId);
        return ResponseEntity.ok(userProfileService.get(userId));
    }

    @PutMapping
    @Operation(summary = "Create or update the user profile (upsert)")
    @ApiResponse(responseCode = "200", description = "User profile saved")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    public ResponseEntity<UserProfileResponse> upsert(@Valid @RequestBody UserProfileRequest request) {
        String userId = userContextProvider.getUserId();
        log.info("PUT /api/v1/profile user={}", userId);
        return ResponseEntity.ok(userProfileService.upsert(request, userId));
    }
}
