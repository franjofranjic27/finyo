package ch.finyo.sync;

public enum SyncStatus {
    RUNNING,
    SUCCESS,
    /** The job ran and blew up. */
    FAILED,
    /** Another run of the same job held the lock, so this one stood down. Not a failure. */
    SKIPPED
}
