package ch.finyo.transaction;

import ch.finyo.common.DocumentProcessingException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure unit tests for CamtParser using inline camt.053.001.04 and .08
 * fixtures (all data fully synthetic). Covers sign handling (DBIT/CRDT,
 * reversal), booking-date fallback, remittance-info joining, counterparty
 * shapes across schema versions, external-reference precedence, status
 * filtering, Sammelbuchung expansion and the entry cap.
 */
class CamtParserTest {

    private final CamtParser parser = new CamtParser();

    // -------------------------------------------------------------------------
    // Fixture builders
    // -------------------------------------------------------------------------

    private static byte[] document(String version, String entries) {
        return ("""
                <?xml version="1.0" encoding="UTF-8"?>
                <Document xmlns="urn:iso:std:iso:20022:tech:xsd:camt.053.001.%s">
                  <BkToCstmrStmt>
                    <GrpHdr><MsgId>SYNTH-MSG-1</MsgId></GrpHdr>
                    <Stmt>
                      <Id>SYNTH-STMT-1</Id>
                      %s
                    </Stmt>
                  </BkToCstmrStmt>
                </Document>
                """.formatted(version, entries)).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] camt04(String entries) {
        return document("04", entries);
    }

    private static byte[] camt08(String entries) {
        return document("08", entries);
    }

    // =========================================================================
    // camt.053.001.04
    // =========================================================================

    @Test
    void debit_entry_is_parsed_with_negative_amount_and_creditor_as_counterparty() {
        List<CamtEntry> entries = parser.parse(camt04("""
                <Ntry>
                  <Amt Ccy="CHF">42.50</Amt>
                  <CdtDbtInd>DBIT</CdtDbtInd>
                  <Sts>BOOK</Sts>
                  <BookgDt><Dt>2025-03-15</Dt></BookgDt>
                  <ValDt><Dt>2025-03-17</Dt></ValDt>
                  <AcctSvcrRef>ENTRY-REF-1</AcctSvcrRef>
                  <NtryDtls>
                    <TxDtls>
                      <Refs><AcctSvcrRef>TX-REF-1</AcctSvcrRef><EndToEndId>E2E-1</EndToEndId></Refs>
                      <RltdPties><Cdtr><Nm>Migros Testfiliale</Nm></Cdtr></RltdPties>
                      <RmtInf><Ustrd>Einkauf Lebensmittel</Ustrd><Ustrd>Kartenzahlung</Ustrd></RmtInf>
                    </TxDtls>
                  </NtryDtls>
                </Ntry>
                """));

        assertThat(entries).hasSize(1);
        CamtEntry entry = entries.get(0);
        assertThat(entry.amount()).isEqualByComparingTo("-42.50");
        assertThat(entry.currency()).isEqualTo("CHF");
        assertThat(entry.date()).isEqualTo(LocalDate.of(2025, Month.MARCH, 15));
        assertThat(entry.description()).isEqualTo("Einkauf Lebensmittel Kartenzahlung");
        assertThat(entry.counterparty()).isEqualTo("Migros Testfiliale");
        assertThat(entry.externalRef()).isEqualTo("TX-REF-1");
    }

    @Test
    void credit_entry_is_parsed_with_positive_amount_and_debtor_as_counterparty() {
        List<CamtEntry> entries = parser.parse(camt04("""
                <Ntry>
                  <Amt Ccy="CHF">2500.00</Amt>
                  <CdtDbtInd>CRDT</CdtDbtInd>
                  <Sts>BOOK</Sts>
                  <BookgDt><Dt>2025-03-25</Dt></BookgDt>
                  <NtryDtls>
                    <TxDtls>
                      <RltdPties><Dbtr><Nm>Muster Arbeitgeber AG</Nm></Dbtr></RltdPties>
                      <RmtInf><Ustrd>Lohn Maerz</Ustrd></RmtInf>
                    </TxDtls>
                  </NtryDtls>
                </Ntry>
                """));

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).amount()).isEqualByComparingTo("2500.00");
        assertThat(entries.get(0).counterparty()).isEqualTo("Muster Arbeitgeber AG");
    }

    @Test
    void reversal_indicator_flips_the_sign_back() {
        List<CamtEntry> entries = parser.parse(camt04("""
                <Ntry>
                  <Amt Ccy="CHF">99.00</Amt>
                  <CdtDbtInd>DBIT</CdtDbtInd>
                  <RvslInd>true</RvslInd>
                  <Sts>BOOK</Sts>
                  <BookgDt><Dt>2025-04-01</Dt></BookgDt>
                </Ntry>
                """));

        // reversed debit = money coming back = positive
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).amount()).isEqualByComparingTo("99.00");
    }

    @Test
    void value_date_is_used_when_the_booking_date_is_missing() {
        List<CamtEntry> entries = parser.parse(camt04("""
                <Ntry>
                  <Amt Ccy="CHF">10.00</Amt>
                  <CdtDbtInd>DBIT</CdtDbtInd>
                  <Sts>BOOK</Sts>
                  <ValDt><DtTm>2025-05-02T08:30:00+02:00</DtTm></ValDt>
                </Ntry>
                """));

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).date()).isEqualTo(LocalDate.of(2025, Month.MAY, 2));
    }

    @Test
    void additional_entry_info_is_the_description_fallback_without_remittance_info() {
        List<CamtEntry> entries = parser.parse(camt04("""
                <Ntry>
                  <Amt Ccy="CHF">5.00</Amt>
                  <CdtDbtInd>DBIT</CdtDbtInd>
                  <Sts>BOOK</Sts>
                  <BookgDt><Dt>2025-05-05</Dt></BookgDt>
                  <AddtlNtryInf>Kontofuehrungsgebuehr</AddtlNtryInf>
                </Ntry>
                """));

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).description()).isEqualTo("Kontofuehrungsgebuehr");
        assertThat(entries.get(0).counterparty()).isNull();
    }

    @Test
    void the_transaction_end_to_end_id_wins_over_the_entry_level_reference() {
        List<CamtEntry> entries = parser.parse(camt04("""
                <Ntry>
                  <Amt Ccy="CHF">10.00</Amt>
                  <CdtDbtInd>DBIT</CdtDbtInd>
                  <Sts>BOOK</Sts>
                  <BookgDt><Dt>2025-05-05</Dt></BookgDt>
                  <AcctSvcrRef>ENTRY-REF-2</AcctSvcrRef>
                  <NtryDtls>
                    <TxDtls>
                      <Refs><EndToEndId>E2E-2</EndToEndId></Refs>
                    </TxDtls>
                  </NtryDtls>
                </Ntry>
                """));

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).externalRef()).isEqualTo("E2E-2");
    }

    @Test
    void entry_level_acct_svcr_ref_is_the_last_resort_for_a_single_transaction_entry() {
        List<CamtEntry> entries = parser.parse(camt04("""
                <Ntry>
                  <Amt Ccy="CHF">10.00</Amt>
                  <CdtDbtInd>DBIT</CdtDbtInd>
                  <Sts>BOOK</Sts>
                  <BookgDt><Dt>2025-05-05</Dt></BookgDt>
                  <AcctSvcrRef>ENTRY-REF-2</AcctSvcrRef>
                  <NtryDtls>
                    <TxDtls>
                      <RmtInf><Ustrd>Ohne Referenzen</Ustrd></RmtInf>
                    </TxDtls>
                  </NtryDtls>
                </Ntry>
                """));

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).externalRef()).isEqualTo("ENTRY-REF-2");
    }

    @Test
    void references_longer_than_the_column_width_are_truncated() {
        String longRef = "R".repeat(300);
        List<CamtEntry> entries = parser.parse(camt04("""
                <Ntry>
                  <Amt Ccy="CHF">10.00</Amt>
                  <CdtDbtInd>DBIT</CdtDbtInd>
                  <Sts>BOOK</Sts>
                  <BookgDt><Dt>2025-05-05</Dt></BookgDt>
                  <NtryDtls>
                    <TxDtls><Refs><AcctSvcrRef>%s</AcctSvcrRef></Refs></TxDtls>
                  </NtryDtls>
                </Ntry>
                """.formatted(longRef)));

        // an untruncated reference would preview fine and then fail the whole commit (@Size(max=255))
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).externalRef()).hasSize(255).isEqualTo("R".repeat(255));
    }

    @Test
    void end_to_end_id_is_the_last_resort_reference_and_notprovided_is_ignored() {
        List<CamtEntry> entries = parser.parse(camt04("""
                <Ntry>
                  <Amt Ccy="CHF">10.00</Amt>
                  <CdtDbtInd>DBIT</CdtDbtInd>
                  <Sts>BOOK</Sts>
                  <BookgDt><Dt>2025-05-05</Dt></BookgDt>
                  <NtryDtls>
                    <TxDtls><Refs><EndToEndId>E2E-3</EndToEndId></Refs></TxDtls>
                  </NtryDtls>
                </Ntry>
                <Ntry>
                  <Amt Ccy="CHF">11.00</Amt>
                  <CdtDbtInd>DBIT</CdtDbtInd>
                  <Sts>BOOK</Sts>
                  <BookgDt><Dt>2025-05-06</Dt></BookgDt>
                  <NtryDtls>
                    <TxDtls><Refs><EndToEndId>NOTPROVIDED</EndToEndId></Refs></TxDtls>
                  </NtryDtls>
                </Ntry>
                """));

        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).externalRef()).isEqualTo("E2E-3");
        assertThat(entries.get(1).externalRef()).isNull();
    }

    @Test
    void non_booked_entries_are_skipped() {
        List<CamtEntry> entries = parser.parse(camt04("""
                <Ntry>
                  <Amt Ccy="CHF">10.00</Amt>
                  <CdtDbtInd>DBIT</CdtDbtInd>
                  <Sts>PDNG</Sts>
                  <BookgDt><Dt>2025-05-05</Dt></BookgDt>
                </Ntry>
                <Ntry>
                  <Amt Ccy="CHF">20.00</Amt>
                  <CdtDbtInd>DBIT</CdtDbtInd>
                  <Sts>BOOK</Sts>
                  <BookgDt><Dt>2025-05-06</Dt></BookgDt>
                </Ntry>
                """));

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).amount()).isEqualByComparingTo("-20.00");
    }

    @Test
    void batch_entry_expands_into_one_row_per_transaction_with_individual_amounts() {
        // Sammelbuchung: entry total 100, two sub-transactions with own amounts
        // (one direct Amt, one AmtDtls/TxAmt in the entry currency) and one
        // without an amount that falls back to the signed entry amount.
        List<CamtEntry> entries = parser.parse(camt04("""
                <Ntry>
                  <Amt Ccy="CHF">100.00</Amt>
                  <CdtDbtInd>DBIT</CdtDbtInd>
                  <Sts>BOOK</Sts>
                  <BookgDt><Dt>2025-06-01</Dt></BookgDt>
                  <AcctSvcrRef>BATCH-REF</AcctSvcrRef>
                  <NtryDtls>
                    <Btch><NbOfTxs>3</NbOfTxs></Btch>
                    <TxDtls>
                      <Refs><AcctSvcrRef>BATCH-TX-1</AcctSvcrRef></Refs>
                      <Amt Ccy="CHF">60.00</Amt>
                      <RmtInf><Ustrd>Teilzahlung eins</Ustrd></RmtInf>
                    </TxDtls>
                    <TxDtls>
                      <Refs><AcctSvcrRef>BATCH-TX-2</AcctSvcrRef></Refs>
                      <AmtDtls><TxAmt><Amt Ccy="CHF">30.00</Amt></TxAmt></AmtDtls>
                      <RmtInf><Ustrd>Teilzahlung zwei</Ustrd></RmtInf>
                    </TxDtls>
                    <TxDtls>
                      <Refs><AcctSvcrRef>BATCH-TX-3</AcctSvcrRef></Refs>
                    </TxDtls>
                  </NtryDtls>
                </Ntry>
                """));

        assertThat(entries).hasSize(3);
        assertThat(entries.get(0).amount()).isEqualByComparingTo("-60.00");
        assertThat(entries.get(0).description()).isEqualTo("Teilzahlung eins");
        assertThat(entries.get(0).externalRef()).isEqualTo("BATCH-TX-1");
        assertThat(entries.get(1).amount()).isEqualByComparingTo("-30.00");
        assertThat(entries.get(1).currency()).isEqualTo("CHF");
        assertThat(entries.get(2).amount()).isEqualByComparingTo("-100.00");
        assertThat(entries.get(2).externalRef()).isEqualTo("BATCH-TX-3");
    }

    @Test
    void batch_sub_transactions_keep_their_own_end_to_end_ids_and_never_share_the_entry_reference() {
        // The entry-level AcctSvcrRef is shared by the whole Sammelbuchung. Handing it to every
        // sub-transaction made them collide on (user, external_ref) and the commit silently dropped
        // all but the first — so sub-transactions use their own refs, or none at all.
        List<CamtEntry> entries = parser.parse(camt04("""
                <Ntry>
                  <Amt Ccy="CHF">75.00</Amt>
                  <CdtDbtInd>DBIT</CdtDbtInd>
                  <Sts>BOOK</Sts>
                  <BookgDt><Dt>2025-06-02</Dt></BookgDt>
                  <AcctSvcrRef>SHARED-ENTRY-REF</AcctSvcrRef>
                  <NtryDtls>
                    <Btch><NbOfTxs>3</NbOfTxs></Btch>
                    <TxDtls>
                      <Refs><EndToEndId>E2E-BATCH-1</EndToEndId></Refs>
                      <Amt Ccy="CHF">25.00</Amt>
                      <RmtInf><Ustrd>Lastschrift eins</Ustrd></RmtInf>
                    </TxDtls>
                    <TxDtls>
                      <Refs><EndToEndId>E2E-BATCH-2</EndToEndId></Refs>
                      <Amt Ccy="CHF">30.00</Amt>
                      <RmtInf><Ustrd>Lastschrift zwei</Ustrd></RmtInf>
                    </TxDtls>
                    <TxDtls>
                      <Refs><EndToEndId>NOTPROVIDED</EndToEndId></Refs>
                      <Amt Ccy="CHF">20.00</Amt>
                      <RmtInf><Ustrd>Lastschrift drei</Ustrd></RmtInf>
                    </TxDtls>
                  </NtryDtls>
                </Ntry>
                """));

        assertThat(entries).hasSize(3);
        assertThat(entries).extracting(CamtEntry::externalRef)
                .containsExactly("E2E-BATCH-1", "E2E-BATCH-2", null);
        assertThat(entries).extracting(CamtEntry::description)
                .containsExactly("Lastschrift eins", "Lastschrift zwei", "Lastschrift drei");
        assertThat(entries).extracting(CamtEntry::amount)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("-25.00"), new BigDecimal("-30.00"), new BigDecimal("-20.00"));
    }

    @Test
    void foreign_currency_tx_amount_is_ignored_in_favour_of_the_entry_amount() {
        // AmtDtls/TxAmt is the amount in the ORIGINAL currency: EUR 30.00 booked as -30.00 into a
        // CHF account would skew every sum, since nothing converts currencies downstream.
        List<CamtEntry> entries = parser.parse(camt04("""
                <Ntry>
                  <Amt Ccy="CHF">32.45</Amt>
                  <CdtDbtInd>DBIT</CdtDbtInd>
                  <Sts>BOOK</Sts>
                  <BookgDt><Dt>2025-06-03</Dt></BookgDt>
                  <NtryDtls>
                    <TxDtls>
                      <Refs><AcctSvcrRef>FX-TX-1</AcctSvcrRef></Refs>
                      <AmtDtls><TxAmt><Amt Ccy="EUR">30.00</Amt></TxAmt></AmtDtls>
                      <RmtInf><Ustrd>Onlineshop EU</Ustrd></RmtInf>
                    </TxDtls>
                  </NtryDtls>
                </Ntry>
                """));

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).amount()).isEqualByComparingTo("-32.45");
        assertThat(entries.get(0).currency()).isEqualTo("CHF");
    }

    // =========================================================================
    // camt.053.001.08 shapes
    // =========================================================================

    @Test
    void version_08_status_code_and_party_shapes_are_supported() {
        List<CamtEntry> entries = parser.parse(camt08("""
                <Ntry>
                  <Amt Ccy="CHF">15.90</Amt>
                  <CdtDbtInd>DBIT</CdtDbtInd>
                  <Sts><Cd>BOOK</Cd></Sts>
                  <BookgDt><Dt>2025-07-10</Dt></BookgDt>
                  <NtryDtls>
                    <TxDtls>
                      <RltdPties><Cdtr><Pty><Nm>Coop Testladen</Nm></Pty></Cdtr></RltdPties>
                      <RmtInf><Ustrd>Einkauf</Ustrd></RmtInf>
                    </TxDtls>
                  </NtryDtls>
                </Ntry>
                <Ntry>
                  <Amt Ccy="CHF">1.00</Amt>
                  <CdtDbtInd>DBIT</CdtDbtInd>
                  <Sts><Cd>PDNG</Cd></Sts>
                  <BookgDt><Dt>2025-07-11</Dt></BookgDt>
                </Ntry>
                """));

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).amount()).isEqualByComparingTo("-15.90");
        assertThat(entries.get(0).counterparty()).isEqualTo("Coop Testladen");
    }

    // =========================================================================
    // Limits
    // =========================================================================

    @Test
    void documents_with_more_than_the_maximum_number_of_entries_are_rejected() {
        String entry = """
                <Ntry>
                  <Amt Ccy="CHF">1.00</Amt>
                  <CdtDbtInd>DBIT</CdtDbtInd>
                  <Sts>BOOK</Sts>
                  <BookgDt><Dt>2025-01-01</Dt></BookgDt>
                </Ntry>
                """;
        byte[] xml = camt04(entry.repeat(10_001));

        assertThatThrownBy(() -> parser.parse(xml))
                .isInstanceOf(DocumentProcessingException.class)
                .hasMessageContaining("maximum");
    }

    @Test
    void empty_input_is_rejected() {
        assertThatThrownBy(() -> parser.parse(new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
