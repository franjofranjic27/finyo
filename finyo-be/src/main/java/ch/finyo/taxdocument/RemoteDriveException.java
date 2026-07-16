package ch.finyo.taxdocument;

/**
 * A call to the remote drive failed.
 *
 * <p>Carries a message we wrote ourselves, never the underlying client's: those
 * embed the full request URL, and a Graph download URL is pre-authenticated —
 * anyone holding it can fetch the tax document without credentials for about an
 * hour. Such a message must never reach a log line or {@code failure_reason}.
 *
 * <p>Treated as transient by the sync: the same file is retried on the next run.
 */
public class RemoteDriveException extends RuntimeException {

    public RemoteDriveException(String message, Throwable cause) {
        super(message, cause);
    }
}
