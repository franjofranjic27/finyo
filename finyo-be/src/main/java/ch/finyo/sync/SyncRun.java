package ch.finyo.sync;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One execution of a background sync.
 *
 * Exists because a job that has been failing silently for three months is indistinguishable
 * from one that never had anything to do. Without a record, "the prices look old" is a
 * mystery; with one it is a question with an answer.
 */
@Entity
@Table(name = "sync_run")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** e.g. {@code prices}, {@code portfolio-snapshots}. */
    @Column(name = "job_name", nullable = false, length = 50)
    private String jobName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SyncStatus status;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    /** How many items the job actually got done — not how many it attempted. */
    @Column(name = "items_processed")
    private Integer itemsProcessed;

    @Column(length = 500)
    private String message;
}
