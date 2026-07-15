package ch.finyo.investment;

/**
 * A preview of what the providers know about an ISIN or valor, for the add-position form.
 *
 * The status is the point, not just the data. The form treats the three outcomes differently:
 * a FOUND security has its master data filled in and its manual-price field hidden (a market
 * price is coming); NOT_FOUND — the normal case for an unlisted 3a fund — keeps the manual
 * fields, because no provider will price it; UNAVAILABLE means the lookup could not run and the
 * user should not be blocked from entering everything by hand.
 */
public record InstrumentLookupResponse(
        Status status,
        String name,
        String ticker,
        String currency,
        AssetClass assetClass
) {

    public enum Status {
        FOUND,
        NOT_FOUND,
        UNAVAILABLE
    }

    static InstrumentLookupResponse notFound() {
        return new InstrumentLookupResponse(Status.NOT_FOUND, null, null, null, null);
    }

    static InstrumentLookupResponse unavailable() {
        return new InstrumentLookupResponse(Status.UNAVAILABLE, null, null, null, null);
    }
}
