package ch.finyoapi.helloworld;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/hello-world")
@RequiredArgsConstructor
@Tag(name = "Hello World", description = "Liveness check endpoint")
public class HelloWorldController {

    private final HelloWorldService helloWorldService;

    @GetMapping
    @Operation(summary = "Greet", description = "Returns a greeting and logs the request.")
    @ApiResponse(responseCode = "200", description = "Greeting returned successfully")
    public ResponseEntity<String> helloWorld() {
        log.info("Received GET /hello-world");
        return ResponseEntity.ok(helloWorldService.greet());
    }
}
