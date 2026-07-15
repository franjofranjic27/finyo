package ch.finyo.sync;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SyncRunResponse(
        UUID id,
        String jobName,
        SyncStatus status,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        Integer itemsProcessed,
        String message
) {
    static SyncRunResponse from(SyncRun run) {
        return new SyncRunResponse(run.getId(), run.getJobName(), run.getStatus(),
                run.getStartedAt(), run.getFinishedAt(), run.getItemsProcessed(), run.getMessage());
    }
}
