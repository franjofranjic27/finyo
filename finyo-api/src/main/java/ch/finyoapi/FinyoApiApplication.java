package ch.finyoapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class FinyoApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinyoApiApplication.class, args);
    }

}
