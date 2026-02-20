package ch.finyoapi.helloworld;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class HelloWorldService {

    private final EndpointLogRepository endpointLogRepository;

    @Transactional
    public String greet() {
        log.info("HelloWorldService: generating greeting");

        String message = "Hello, I'm finyo!";

        EndpointLog logEntry = EndpointLog.builder()
                .message(message)
                .calledAt(OffsetDateTime.now())
                .endpoint("/hello-world")
                .build();

        endpointLogRepository.save(logEntry);
        log.info("HelloWorldService: persisted endpoint log with id={}", logEntry.getId());

        return message;
    }
}
