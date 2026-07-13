package ch.finyo.transaction;

import ch.finyo.common.DocumentProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * StAX-based parser for ISO 20022 camt.053 bank statements. Matching is done
 * on local element names only, so all namespace versions in the wild
 * (camt.053.001.02/.04/.08) parse with the same code.
 *
 * <p>Extraction rules per booked ({@code Sts=BOOK}) entry:
 * <ul>
 *   <li>Sign: {@code CdtDbtInd=DBIT} negates the amount, {@code RvslInd=true}
 *       flips the sign again.</li>
 *   <li>Date: {@code BookgDt} (date part of {@code Dt}/{@code DtTm}),
 *       falling back to {@code ValDt}.</li>
 *   <li>Sammelbuchungen: one {@link CamtEntry} per {@code TxDtls}, using the
 *       per-transaction amount ({@code Amt} or {@code AmtDtls/TxAmt/Amt})
 *       when present, else the entry amount. Limitation: the entry-level
 *       debit/credit sign is applied to every sub-transaction — mixed-sign
 *       batches are not decomposed.</li>
 *   <li>External reference precedence: {@code TxDtls/Refs/AcctSvcrRef} &gt;
 *       entry-level {@code AcctSvcrRef} &gt; {@code EndToEndId} (the literal
 *       {@code NOTPROVIDED} is ignored); may be null.</li>
 * </ul>
 *
 * <p>Security hardening: DTDs and external entities are disabled and any DTD
 * event aborts parsing (XXE / billion laughs). Documents are capped at
 * {@value #MAX_ENTRIES} entries and element text is truncated at
 * {@value #MAX_TEXT_LENGTH} characters. File contents are never logged —
 * counts only.
 */
@Slf4j
@Component
public class CamtParser {

    private static final int MAX_ENTRIES = 10_000;
    private static final int MAX_TEXT_LENGTH = 4_096;
    private static final String NOT_PROVIDED = "NOTPROVIDED";
    private static final String STATUS_BOOKED = "BOOK";
    private static final String NOT_CAMT_MESSAGE = "The file is not a camt.053 document";

    public List<CamtEntry> parse(byte[] xmlBytes) {
        if (xmlBytes == null || xmlBytes.length == 0) {
            throw new IllegalArgumentException("File is empty");
        }
        XMLStreamReader reader = null;
        try {
            reader = createHardenedFactory().createXMLStreamReader(new ByteArrayInputStream(xmlBytes));
            List<CamtEntry> entries = parseDocument(reader);
            log.info("camt.053 parsed: entries={}", entries.size());
            return entries;
        } catch (DocumentProcessingException e) {
            throw e;
        } catch (XMLStreamException | RuntimeException e) {
            log.warn("camt.053 parsing failed: {}", e.getClass().getSimpleName());
            throw new DocumentProcessingException(NOT_CAMT_MESSAGE);
        } finally {
            closeQuietly(reader);
        }
    }

    private static XMLInputFactory createHardenedFactory() {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
        return factory;
    }

    private List<CamtEntry> parseDocument(XMLStreamReader reader) throws XMLStreamException {
        if (!"Document".equals(advanceToRootElement(reader))) {
            throw new DocumentProcessingException(NOT_CAMT_MESSAGE);
        }
        List<CamtEntry> entries = new ArrayList<>();
        boolean statementFound = false;
        int entryElementCount = 0;
        while (reader.hasNext()) {
            int event = nextEvent(reader);
            if (event == XMLStreamConstants.START_ELEMENT) {
                String name = reader.getLocalName();
                if ("BkToCstmrStmt".equals(name)) {
                    statementFound = true;
                } else if ("Ntry".equals(name)) {
                    entryElementCount++;
                    parseEntry(reader, entries);
                    if (entryElementCount > MAX_ENTRIES || entries.size() > MAX_ENTRIES) {
                        throw new DocumentProcessingException(
                                "The camt.053 document exceeds the maximum of " + MAX_ENTRIES + " entries");
                    }
                }
            }
        }
        if (!statementFound) {
            throw new DocumentProcessingException(NOT_CAMT_MESSAGE);
        }
        return List.copyOf(entries);
    }

    /** Skips the prolog (rejecting DTDs) and returns the local name of the root element. */
    private String advanceToRootElement(XMLStreamReader reader) throws XMLStreamException {
        while (reader.hasNext()) {
            if (nextEvent(reader) == XMLStreamConstants.START_ELEMENT) {
                return reader.getLocalName();
            }
        }
        throw new DocumentProcessingException(NOT_CAMT_MESSAGE);
    }

    // -------------------------------------------------------------------------
    // Entry parsing
    // -------------------------------------------------------------------------

    private record TxDetail(@Nullable BigDecimal amount, @Nullable String currency,
                            @Nullable String acctSvcrRef, @Nullable String endToEndId,
                            @Nullable String description, @Nullable String creditorName,
                            @Nullable String debtorName) {
    }

    /** Reader is positioned at the {@code Ntry} start element; consumes the whole subtree. */
    private void parseEntry(XMLStreamReader reader, List<CamtEntry> out) throws XMLStreamException {
        BigDecimal amount = null;
        String currency = null;
        boolean debit = false;
        boolean reversal = false;
        String status = null;
        String bookingDateText = null;
        String valueDateText = null;
        String entryRef = null;
        String additionalInfo = null;
        List<TxDetail> details = new ArrayList<>();

        while (true) {
            int event = nextEvent(reader);
            if (event == XMLStreamConstants.END_ELEMENT) {
                break; // every child handler consumes its subtree, so this closes Ntry
            }
            if (event != XMLStreamConstants.START_ELEMENT) {
                continue;
            }
            switch (reader.getLocalName()) {
                case "Amt" -> {
                    currency = reader.getAttributeValue(null, "Ccy");
                    amount = new BigDecimal(readElementText(reader));
                }
                case "CdtDbtInd" -> debit = "DBIT".equalsIgnoreCase(readElementText(reader));
                case "RvslInd" -> reversal = Boolean.parseBoolean(readElementText(reader));
                // .02/.04 carry the code as direct text, .08 wraps it in a Cd
                // child — readElementText collects text from the whole subtree
                case "Sts" -> status = readElementText(reader);
                case "BookgDt" -> bookingDateText = readElementText(reader);
                case "ValDt" -> valueDateText = readElementText(reader);
                case "AcctSvcrRef" -> entryRef = emptyToNull(readElementText(reader));
                case "AddtlNtryInf" -> additionalInfo = emptyToNull(readElementText(reader));
                case "NtryDtls" -> parseEntryDetails(reader, details);
                default -> skipElement(reader);
            }
        }

        if (!STATUS_BOOKED.equalsIgnoreCase(status)) {
            return;
        }
        if (amount == null) {
            throw new DocumentProcessingException("The camt.053 document contains an entry without an amount");
        }
        LocalDate date = parseDate(firstNonBlank(bookingDateText, valueDateText));
        if (date == null) {
            throw new DocumentProcessingException("The camt.053 document contains an entry without a booking date");
        }

        boolean negative = debit ^ reversal;
        BigDecimal entryAmount = negative ? amount.negate() : amount;

        if (details.isEmpty()) {
            out.add(new CamtEntry(date, entryAmount, currency, additionalInfo, null, entryRef));
            return;
        }
        for (TxDetail detail : details) {
            BigDecimal detailAmount = detail.amount() != null
                    ? (negative ? detail.amount().negate() : detail.amount())
                    : entryAmount;
            String detailCurrency = detail.currency() != null ? detail.currency() : currency;
            String description = firstNonBlank(detail.description(), additionalInfo);
            String counterparty = negative ? detail.creditorName() : detail.debtorName();
            String externalRef = firstNonBlank(detail.acctSvcrRef(), entryRef, detail.endToEndId());
            out.add(new CamtEntry(date, detailAmount, detailCurrency, description, counterparty, externalRef));
        }
    }

    /** Reader is positioned at {@code NtryDtls}; collects every {@code TxDtls} child. */
    private void parseEntryDetails(XMLStreamReader reader, List<TxDetail> details) throws XMLStreamException {
        while (true) {
            int event = nextEvent(reader);
            if (event == XMLStreamConstants.END_ELEMENT) {
                return;
            }
            if (event != XMLStreamConstants.START_ELEMENT) {
                continue;
            }
            if ("TxDtls".equals(reader.getLocalName())) {
                details.add(parseTxDetails(reader));
            } else {
                skipElement(reader);
            }
        }
    }

    private TxDetail parseTxDetails(XMLStreamReader reader) throws XMLStreamException {
        BigDecimal amount = null;
        String currency = null;
        String acctSvcrRef = null;
        String endToEndId = null;
        List<String> remittanceLines = new ArrayList<>();
        String creditorName = null;
        String debtorName = null;

        while (true) {
            int event = nextEvent(reader);
            if (event == XMLStreamConstants.END_ELEMENT) {
                break;
            }
            if (event != XMLStreamConstants.START_ELEMENT) {
                continue;
            }
            switch (reader.getLocalName()) {
                case "Refs" -> {
                    RefValues refs = parseRefs(reader);
                    acctSvcrRef = refs.acctSvcrRef();
                    endToEndId = refs.endToEndId();
                }
                case "Amt" -> {
                    currency = reader.getAttributeValue(null, "Ccy");
                    amount = new BigDecimal(readElementText(reader));
                }
                case "AmtDtls" -> {
                    AmountValue txAmount = parseAmountDetails(reader);
                    if (amount == null && txAmount != null) {
                        amount = txAmount.amount();
                        currency = txAmount.currency();
                    }
                }
                case "RmtInf" -> collectRemittanceLines(reader, remittanceLines);
                case "RltdPties" -> {
                    PartyNames parties = parseRelatedParties(reader);
                    creditorName = parties.creditorName();
                    debtorName = parties.debtorName();
                }
                default -> skipElement(reader);
            }
        }

        String description = remittanceLines.isEmpty() ? null : String.join(" ", remittanceLines);
        return new TxDetail(amount, currency, acctSvcrRef, endToEndId, description, creditorName, debtorName);
    }

    private record RefValues(@Nullable String acctSvcrRef, @Nullable String endToEndId) {}

    private RefValues parseRefs(XMLStreamReader reader) throws XMLStreamException {
        String acctSvcrRef = null;
        String endToEndId = null;
        while (true) {
            int event = nextEvent(reader);
            if (event == XMLStreamConstants.END_ELEMENT) {
                return new RefValues(acctSvcrRef, endToEndId);
            }
            if (event != XMLStreamConstants.START_ELEMENT) {
                continue;
            }
            switch (reader.getLocalName()) {
                case "AcctSvcrRef" -> acctSvcrRef = emptyToNull(readElementText(reader));
                case "EndToEndId" -> {
                    String value = emptyToNull(readElementText(reader));
                    endToEndId = NOT_PROVIDED.equalsIgnoreCase(value) ? null : value;
                }
                default -> skipElement(reader);
            }
        }
    }

    private record AmountValue(BigDecimal amount, @Nullable String currency) {}

    /** Reads {@code AmtDtls/TxAmt/Amt} — the amount location used by most real-world statements. */
    private @Nullable AmountValue parseAmountDetails(XMLStreamReader reader) throws XMLStreamException {
        AmountValue result = null;
        while (true) {
            int event = nextEvent(reader);
            if (event == XMLStreamConstants.END_ELEMENT) {
                return result;
            }
            if (event != XMLStreamConstants.START_ELEMENT) {
                continue;
            }
            if ("TxAmt".equals(reader.getLocalName()) && result == null) {
                result = parseTxAmt(reader);
            } else {
                skipElement(reader);
            }
        }
    }

    private @Nullable AmountValue parseTxAmt(XMLStreamReader reader) throws XMLStreamException {
        AmountValue result = null;
        while (true) {
            int event = nextEvent(reader);
            if (event == XMLStreamConstants.END_ELEMENT) {
                return result;
            }
            if (event != XMLStreamConstants.START_ELEMENT) {
                continue;
            }
            if ("Amt".equals(reader.getLocalName()) && result == null) {
                String currency = reader.getAttributeValue(null, "Ccy");
                result = new AmountValue(new BigDecimal(readElementText(reader)), currency);
            } else {
                skipElement(reader);
            }
        }
    }

    private void collectRemittanceLines(XMLStreamReader reader, List<String> lines) throws XMLStreamException {
        while (true) {
            int event = nextEvent(reader);
            if (event == XMLStreamConstants.END_ELEMENT) {
                return;
            }
            if (event != XMLStreamConstants.START_ELEMENT) {
                continue;
            }
            if ("Ustrd".equals(reader.getLocalName())) {
                String line = readElementText(reader);
                if (!line.isEmpty()) {
                    lines.add(line);
                }
            } else {
                skipElement(reader);
            }
        }
    }

    private record PartyNames(@Nullable String creditorName, @Nullable String debtorName) {}

    private PartyNames parseRelatedParties(XMLStreamReader reader) throws XMLStreamException {
        String creditorName = null;
        String debtorName = null;
        while (true) {
            int event = nextEvent(reader);
            if (event == XMLStreamConstants.END_ELEMENT) {
                return new PartyNames(creditorName, debtorName);
            }
            if (event != XMLStreamConstants.START_ELEMENT) {
                continue;
            }
            switch (reader.getLocalName()) {
                case "Cdtr" -> creditorName = parsePartyName(reader);
                case "Dbtr" -> debtorName = parsePartyName(reader);
                default -> skipElement(reader);
            }
        }
    }

    /**
     * Returns the first {@code Nm} found anywhere below the party element —
     * covers both the {@code Cdtr/Nm} (≤ .04) and {@code Cdtr/Pty/Nm} (.08)
     * shapes.
     */
    private @Nullable String parsePartyName(XMLStreamReader reader) throws XMLStreamException {
        String name = null;
        int depth = 1;
        while (depth > 0) {
            int event = nextEvent(reader);
            if (event == XMLStreamConstants.START_ELEMENT) {
                if (name == null && "Nm".equals(reader.getLocalName())) {
                    name = emptyToNull(readElementText(reader)); // consumes Nm, depth unchanged
                } else {
                    depth++;
                }
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
            }
        }
        return name;
    }

    // -------------------------------------------------------------------------
    // Low-level StAX helpers
    // -------------------------------------------------------------------------

    /** Single choke point for events: a DTD anywhere in the stream aborts parsing. */
    private int nextEvent(XMLStreamReader reader) throws XMLStreamException {
        int event = reader.next();
        if (event == XMLStreamConstants.DTD) {
            log.warn("camt.053 rejected: document contains a DTD");
            throw new DocumentProcessingException("XML documents with a DTD are not supported");
        }
        return event;
    }

    /**
     * Collects the character data of the current element's whole subtree,
     * truncated at {@value #MAX_TEXT_LENGTH} characters. Entity references
     * are never resolved (see factory settings) and simply ignored.
     */
    private String readElementText(XMLStreamReader reader) throws XMLStreamException {
        StringBuilder text = new StringBuilder();
        int depth = 1;
        while (depth > 0) {
            int event = nextEvent(reader);
            switch (event) {
                case XMLStreamConstants.START_ELEMENT -> depth++;
                case XMLStreamConstants.END_ELEMENT -> depth--;
                case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA -> {
                    if (text.length() < MAX_TEXT_LENGTH) {
                        text.append(reader.getText());
                        if (text.length() > MAX_TEXT_LENGTH) {
                            text.setLength(MAX_TEXT_LENGTH);
                        }
                    }
                }
                default -> { /* comments, PIs, unresolved entity refs: ignored */ }
            }
        }
        return text.toString().strip();
    }

    /** Consumes the current element's subtree without extracting anything. */
    private void skipElement(XMLStreamReader reader) throws XMLStreamException {
        int depth = 1;
        while (depth > 0) {
            int event = nextEvent(reader);
            if (event == XMLStreamConstants.START_ELEMENT) {
                depth++;
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                depth--;
            }
        }
    }

    private static @Nullable LocalDate parseDate(@Nullable String text) {
        if (text == null || text.length() < 10) {
            return null;
        }
        return LocalDate.parse(text.substring(0, 10));
    }

    private static @Nullable String firstNonBlank(@Nullable String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static @Nullable String emptyToNull(String value) {
        return value.isBlank() ? null : value;
    }

    private static void closeQuietly(@Nullable XMLStreamReader reader) {
        if (reader == null) {
            return;
        }
        try {
            reader.close();
        } catch (XMLStreamException e) {
            log.debug("Failed to close XML reader: {}", e.getClass().getSimpleName());
        }
    }
}
