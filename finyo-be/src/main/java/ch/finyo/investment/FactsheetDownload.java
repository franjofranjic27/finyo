package ch.finyo.investment;

/** A stored factsheet PDF ready for download. */
public record FactsheetDownload(
        String filename,
        byte[] content
) {}
