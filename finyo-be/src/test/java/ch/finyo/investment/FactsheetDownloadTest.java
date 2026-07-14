package ch.finyo.investment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FactsheetDownloadTest {

    @Test
    void equals_and_hashCode_consider_array_content() {
        FactsheetDownload a = new FactsheetDownload("f.pdf", new byte[]{1, 2, 3});
        FactsheetDownload b = new FactsheetDownload("f.pdf", new byte[]{1, 2, 3});

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }

    @Test
    void equals_detects_differing_filename_or_content() {
        FactsheetDownload base = new FactsheetDownload("f.pdf", new byte[]{1, 2, 3});

        assertThat(base)
                .isNotEqualTo(new FactsheetDownload("g.pdf", new byte[]{1, 2, 3}))
                .isNotEqualTo(new FactsheetDownload("f.pdf", new byte[]{9}))
                .isNotEqualTo("not a download");
    }

    @Test
    void toString_reports_content_length_instead_of_raw_bytes() {
        assertThat(new FactsheetDownload("f.pdf", new byte[]{1, 2, 3}))
                .hasToString("FactsheetDownload[filename=f.pdf, content=3 bytes]");
        assertThat(new FactsheetDownload("f.pdf", null))
                .hasToString("FactsheetDownload[filename=f.pdf, content=0 bytes]");
    }
}
