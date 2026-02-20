package ch.finyoapi.helloworld;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class HelloWorldService {

    public String greet() {
        log.info("HelloWorldService: generating greeting");
        return "Hello, I'm finyo!";
    }
}
