package ch.finyoapi.helloworld;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "endpoint_log")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EndpointLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String message;

    @Column(name = "called_at", nullable = false)
    private OffsetDateTime calledAt;

    @Column(nullable = false, length = 255)
    private String endpoint;
}
