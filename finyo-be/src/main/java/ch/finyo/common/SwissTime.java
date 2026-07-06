package ch.finyo.common;

import java.time.ZoneId;

/**
 * Shared time-zone constant for business-date logic (tax deadlines, month
 * boundaries, savings projections). All user-facing dates in this Swiss
 * finance domain are interpreted in the Zurich time zone; persistence and
 * auditing timestamps use UTC instead.
 */
public final class SwissTime {

    public static final ZoneId ZONE = ZoneId.of("Europe/Zurich");

    private SwissTime() {
    }
}
