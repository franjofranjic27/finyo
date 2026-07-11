package ch.finyo.taxdocument;

import ch.finyo.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the multipart upload endpoints under
 * POST /api/v1/tax/documents (classify + the five typed extraction endpoints).
 *
 * Complements the extractor/classifier unit tests (which work on raw fixture
 * text) with the full HTTP + PDF path: real PDFs rendered from the fixtures
 * via {@link TestPdfFactory}, PDFBox text extraction, the classification
 * guard on typed endpoints and the ProblemDetail error contract.
 */
class TaxDocumentIT extends BaseIntegrationTest {

    private static final String DOCUMENT_PROCESSING_ERROR_TYPE = "https://finyo.ch/errors/document-processing";

    @Autowired
    private MockMvc mockMvc;

    private MockMultipartFile fixturePdf(String fixtureName) {
        return new MockMultipartFile("file", fixtureName.replace(".txt", ".pdf"),
                "application/pdf", TestPdfFactory.pdfFromFixture(fixtureName));
    }

    @Test
    void salary_certificate_upload_extracts_gross_salary_mapped_to_gross_employment_income() throws Exception {
        mockMvc.perform(multipart("/api/v1/tax/documents/salary-certificate")
                        .file(fixturePdf("salary-certificate.txt"))
                        .with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type", is("SALARY_CERTIFICATE")))
                .andExpect(jsonPath("$.taxYear", is(2025)))
                .andExpect(jsonPath("$.fields.grossSalary", is(84000)))
                .andExpect(jsonPath("$.fields.netSalary", is(74466)))
                .andExpect(jsonPath("$.taxYearMapping[0].fieldName", is("grossSalary")))
                .andExpect(jsonPath("$.taxYearMapping[0].value", is(84000)))
                .andExpect(jsonPath("$.taxYearMapping[0].targetTaxYearField", is("grossEmploymentIncome")));
    }

    @Test
    void health_insurance_upload_extracts_total_premium_mapped_to_insurance_deduction() throws Exception {
        mockMvc.perform(multipart("/api/v1/tax/documents/health-insurance")
                        .file(fixturePdf("health-insurance-sanitas.txt"))
                        .with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type", is("HEALTH_INSURANCE")))
                .andExpect(jsonPath("$.taxYear", is(2025)))
                .andExpect(jsonPath("$.fields.basicPremium", is(3612.00)))
                .andExpect(jsonPath("$.fields.totalPremium", is(4500.00)))
                .andExpect(jsonPath("$.taxYearMapping[0].fieldName", is("totalPremium")))
                .andExpect(jsonPath("$.taxYearMapping[0].value", is(4500.00)))
                .andExpect(jsonPath("$.taxYearMapping[0].targetTaxYearField", is("deductionInsurancePremiums")));
    }

    @Test
    void securities_statement_upload_extracts_total_tax_value_mapped_to_net_wealth() throws Exception {
        mockMvc.perform(multipart("/api/v1/tax/documents/securities-statement")
                        .file(fixturePdf("securities-raiffeisen.txt"))
                        .with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type", is("SECURITIES_STATEMENT")))
                .andExpect(jsonPath("$.taxYear", is(2025)))
                .andExpect(jsonPath("$.fields.totalTaxValue", is(89666.50)))
                .andExpect(jsonPath("$.fields.totalGrossIncome", is(430.05)))
                .andExpect(jsonPath("$.taxYearMapping[0].fieldName", is("totalTaxValue")))
                .andExpect(jsonPath("$.taxYearMapping[0].value", is(89666.50)))
                .andExpect(jsonPath("$.taxYearMapping[0].targetTaxYearField", is("netWealth")));
    }

    @Test
    void pillar3a_upload_extracts_contribution_mapped_to_pillar3a_contribution() throws Exception {
        mockMvc.perform(multipart("/api/v1/tax/documents/pillar3a")
                        .file(fixturePdf("pillar3a-form21.txt"))
                        .with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type", is("PILLAR_3A")))
                .andExpect(jsonPath("$.taxYear", is(2025)))
                .andExpect(jsonPath("$.fields.contribution", is(7056)))
                .andExpect(jsonPath("$.fields.institution", is("Muster Vorsorgestiftung")))
                .andExpect(jsonPath("$.taxYearMapping[0].fieldName", is("contribution")))
                .andExpect(jsonPath("$.taxYearMapping[0].value", is(7056)))
                .andExpect(jsonPath("$.taxYearMapping[0].targetTaxYearField", is("pillar3aContribution")));
    }

    @Test
    void assessment_upload_extracts_tax_amount_mapped_to_assessed_amount() throws Exception {
        mockMvc.perform(multipart("/api/v1/tax/documents/assessment")
                        .file(fixturePdf("assessment-sg.txt"))
                        .with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type", is("ASSESSMENT")))
                .andExpect(jsonPath("$.taxYear", is(2025)))
                .andExpect(jsonPath("$.fields.taxType", is("KGSt")))
                .andExpect(jsonPath("$.fields.taxableIncome", is(64300)))
                .andExpect(jsonPath("$.fields.taxableWealth", is(89000)))
                .andExpect(jsonPath("$.fields.taxAmount", is(6480.00)))
                .andExpect(jsonPath("$.taxYearMapping[0].fieldName", is("taxAmount")))
                .andExpect(jsonPath("$.taxYearMapping[0].value", is(6480.00)))
                .andExpect(jsonPath("$.taxYearMapping[0].targetTaxYearField", is("assessedAmount")));
    }

    @Test
    void classify_detects_a_salary_certificate_with_confidence() throws Exception {
        mockMvc.perform(multipart("/api/v1/tax/documents/classify")
                        .file(fixturePdf("salary-certificate.txt"))
                        .with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detectedType", is("SALARY_CERTIFICATE")))
                .andExpect(jsonPath("$.confidence", greaterThan(0.5)));
    }

    @Test
    void classify_detects_a_pillar3a_certificate_with_confidence() throws Exception {
        mockMvc.perform(multipart("/api/v1/tax/documents/classify")
                        .file(fixturePdf("pillar3a-form21.txt"))
                        .with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detectedType", is("PILLAR_3A")))
                .andExpect(jsonPath("$.confidence", greaterThan(0.5)));
    }

    @Test
    void pdf_without_text_layer_is_rejected_as_scan_with_422() throws Exception {
        MockMultipartFile scannedPdf = new MockMultipartFile("file", "scan.pdf",
                "application/pdf", TestPdfFactory.blankPagePdf());

        mockMvc.perform(multipart("/api/v1/tax/documents/classify")
                        .file(scannedPdf)
                        .with(asUser()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type", is(DOCUMENT_PROCESSING_ERROR_TYPE)))
                .andExpect(jsonPath("$.title", is("Document Processing Failed")));
    }

    @Test
    void pillar3a_document_uploaded_to_salary_endpoint_is_rejected_with_detected_type() throws Exception {
        mockMvc.perform(multipart("/api/v1/tax/documents/salary-certificate")
                        .file(fixturePdf("pillar3a-form21.txt"))
                        .with(asUser()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type", is(DOCUMENT_PROCESSING_ERROR_TYPE)))
                .andExpect(jsonPath("$.detectedType", is("PILLAR_3A")));
    }

    @Test
    void plain_text_upload_is_rejected_as_not_a_pdf_with_400() throws Exception {
        MockMultipartFile textFile = new MockMultipartFile("file", "notes.txt",
                "text/plain", "Bruttolohn total 84000".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/tax/documents/salary-certificate")
                        .file(textFile)
                        .with(asUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", is("https://finyo.ch/errors/bad-request")));
    }

    @Test
    void upload_without_authentication_returns_401() throws Exception {
        mockMvc.perform(multipart("/api/v1/tax/documents/classify")
                        .file(fixturePdf("salary-certificate.txt")))
                .andExpect(status().isUnauthorized());
    }
}
