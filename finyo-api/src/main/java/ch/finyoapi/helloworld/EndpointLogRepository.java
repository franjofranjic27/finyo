package ch.finyoapi.helloworld;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EndpointLogRepository extends JpaRepository<EndpointLog, Long> {
}
