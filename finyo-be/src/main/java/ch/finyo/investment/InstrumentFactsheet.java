package ch.finyo.investment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Uploaded factsheet PDF of an instrument. Deliberately a separate table so
 * the (up to 10 MB) blob is only fetched by the dedicated factsheet
 * endpoints — portfolio and detail reads never touch it.
 */
@Entity
@Table(name = "instrument_factsheet")
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class InstrumentFactsheet {

    /** One factsheet per instrument — the instrument id doubles as primary key. */
    @Id
    @Column(name = "instrument_id")
    private UUID instrumentId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(nullable = false)
    private byte[] pdf;

    @Column(nullable = false, length = 255)
    private String filename;

    @Column(nullable = false)
    private long size;

    @Column(name = "uploaded_at", nullable = false)
    private OffsetDateTime uploadedAt;
}
