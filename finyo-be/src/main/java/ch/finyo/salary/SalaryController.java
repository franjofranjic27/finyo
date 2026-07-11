package ch.finyo.salary;

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
@RequestMapping("/api/v1/salary")
@RequiredArgsConstructor
@Tag(name = "Salary", description = "Per-user Swiss gross-to-net salary calculator")
public class SalaryController {

    private final SalaryService salaryService;
    private final UserContextProvider userContextProvider;

    @GetMapping
    @Operation(summary = "Get the salary profile with the computed gross-to-net result; "
            + "returns Swiss default rates when none is set")
    @ApiResponse(responseCode = "200", description = "Salary profile returned successfully")
    public ResponseEntity<SalaryResponse> get() {
        String userId = userContextProvider.getUserId();
        log.info("GET /api/v1/salary user={}", userId);
        return ResponseEntity.ok(salaryService.get(userId));
    }

    @PutMapping
    @Operation(summary = "Create or update the salary profile (upsert)")
    @ApiResponse(responseCode = "200", description = "Salary profile saved")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    public ResponseEntity<SalaryResponse> upsert(@Valid @RequestBody SalaryRequest request) {
        String userId = userContextProvider.getUserId();
        log.info("PUT /api/v1/salary user={}", userId);
        return ResponseEntity.ok(salaryService.upsert(request, userId));
    }
}
