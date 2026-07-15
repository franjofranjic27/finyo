package ch.finyo.sync;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SyncRunRepository extends JpaRepository<SyncRun, UUID> {

    List<SyncRun> findTop20ByOrderByStartedAtDesc();

}
