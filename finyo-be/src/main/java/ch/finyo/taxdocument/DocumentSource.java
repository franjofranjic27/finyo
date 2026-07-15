package ch.finyo.taxdocument;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A cloud folder finyo ingests documents from, bound to the user who owns it.
 *
 * <p>The sync job has no request and therefore no SecurityContext, so it cannot
 * read the user from a JWT — the ownership lives here.
 */
@Entity
@Table(name = "document_source")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class DocumentSource {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "drive_id", nullable = false)
    private String driveId;

    /** Prefix filter applied client-side: the Graph delta feed cannot filter by path. */
    @Column(name = "root_folder_path", nullable = false, length = 1024)
    private String rootFolderPath;

    @Column(name = "delta_link", columnDefinition = "TEXT")
    private @Nullable String deltaLink;

    @Column(name = "sync_started_at")
    private @Nullable OffsetDateTime syncStartedAt;

    @Column(name = "last_sync_at")
    private @Nullable OffsetDateTime lastSyncAt;

    @Column(nullable = false)
    private boolean enabled;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
