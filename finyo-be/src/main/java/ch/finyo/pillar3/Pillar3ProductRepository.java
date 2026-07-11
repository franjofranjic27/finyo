package ch.finyo.pillar3;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface Pillar3ProductRepository extends JpaRepository<Pillar3Product, UUID> {

    Optional<Pillar3Product> findByIsin(String isin);

    List<Pillar3Product> findByActiveTrueOrderBySortOrderAscNameAsc();

    List<Pillar3Product> findAllByOrderBySortOrderAscNameAsc();

    @Query("""
        SELECT p FROM Pillar3Product p
        WHERE p.active = TRUE AND (
            LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(p.provider) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(p.isin) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(p.valor) LIKE LOWER(CONCAT('%', :search, '%'))
        )
        ORDER BY p.sortOrder ASC, p.name ASC
        """)
    List<Pillar3Product> searchActive(@Param("search") String search);
}
