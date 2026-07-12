package ch.finyo.account;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentCardRepository extends JpaRepository<PaymentCard, UUID> {

    List<PaymentCard> findByUserIdOrderByNameAsc(String userId);

    Optional<PaymentCard> findByIdAndUserId(UUID id, String userId);

    boolean existsByIdAndUserId(UUID id, String userId);
}
