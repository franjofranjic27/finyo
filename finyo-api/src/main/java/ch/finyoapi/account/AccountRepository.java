package ch.finyoapi.account;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    List<Account> findByUserIdOrderByNameAsc(String userId);

    Optional<Account> findByIdAndUserId(UUID id, String userId);

    boolean existsByIdAndUserId(UUID id, String userId);
}
