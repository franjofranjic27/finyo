package ch.finyo.investment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface InstrumentFactsheetRepository extends JpaRepository<InstrumentFactsheet, UUID> {

    /** Metadata-only view of a stored factsheet — the pdf bytes are never selected. */
    interface FactsheetMetadata {
        String getFilename();

        long getSize();

        OffsetDateTime getUploadedAt();
    }

    /** Loads the full row including the blob — download endpoint only. */
    Optional<InstrumentFactsheet> findByInstrumentIdAndUserId(UUID instrumentId, String userId);

    /** Closed interface projection: Hibernate selects only the metadata columns. */
    Optional<FactsheetMetadata> findMetadataByInstrumentIdAndUserId(UUID instrumentId, String userId);

    boolean existsByInstrumentIdAndUserId(UUID instrumentId, String userId);

    /**
     * Atomically inserts or replaces the factsheet of an instrument. The
     * single ON CONFLICT statement avoids the read-then-write race and —
     * unlike a JPA merge — never loads the previous blob just to overwrite it.
     */
    @Transactional
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO instrument_factsheet (instrument_id, user_id, pdf, filename, size, uploaded_at)
            VALUES (:instrumentId, :userId, :pdf, :filename, :size, :uploadedAt)
            ON CONFLICT (instrument_id)
            DO UPDATE SET pdf = EXCLUDED.pdf, filename = EXCLUDED.filename,
                          size = EXCLUDED.size, uploaded_at = EXCLUDED.uploaded_at
            """)
    void upsert(@Param("instrumentId") UUID instrumentId,
                @Param("userId") String userId,
                @Param("pdf") byte[] pdf,
                @Param("filename") String filename,
                @Param("size") long size,
                @Param("uploadedAt") OffsetDateTime uploadedAt);

    /**
     * Bulk delete without loading the blob first.
     *
     * @return number of deleted rows — 0 when no factsheet was stored
     */
    @Transactional
    @Modifying
    @Query("DELETE FROM InstrumentFactsheet f WHERE f.instrumentId = :instrumentId AND f.userId = :userId")
    int deleteByInstrumentIdAndUserId(@Param("instrumentId") UUID instrumentId, @Param("userId") String userId);
}
