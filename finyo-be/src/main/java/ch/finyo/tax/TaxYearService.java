package ch.finyo.tax;

import ch.finyo.common.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaxYearService {

    private final TaxYearRepository taxYearRepository;
    private final TaxPaymentRepository taxPaymentRepository;
    private final TaxDeadlineRepository taxDeadlineRepository;
    private final TaxCalculationService taxCalculationService;

    public List<TaxYearSummaryResponse> getAll(String userId) {
        log.debug("Fetching all tax years for user={}", userId);
        return taxYearRepository.findByUserIdOrderByTaxYearDesc(userId)
                .stream()
                .map(taxYear -> toSummary(taxYear, userId))
                .toList();
    }

    public TaxYearDetailResponse getByYear(int year, String userId) {
        log.debug("Fetching tax year={} for user={}", year, userId);
        return toDetail(loadYear(year, userId), userId);
    }

    @Transactional
    public TaxYearDetailResponse upsert(int year, TaxYearRequest request, String userId) {
        log.info("Upserting tax year={} for user={}", year, userId);
        TaxYear.TaxYearBuilder builder = taxYearRepository.findByUserIdAndTaxYear(userId, year)
                .map(TaxYear::toBuilder)
                .orElseGet(() -> TaxYear.builder()
                        .userId(userId)
                        .taxYear(year)
                        .status(TaxYearStatus.OPEN));

        builder.cantonCode(request.cantonCode())
                .bfsNumber(request.bfsNumber())
                .civilStatus(request.civilStatus())
                .numberOfChildren(request.numberOfChildren())
                .churchAffiliation(request.churchAffiliation())
                .grossEmploymentIncome(request.grossEmploymentIncome())
                .selfEmploymentIncome(request.selfEmploymentIncome())
                .investmentIncome(request.investmentIncome())
                .rentalIncome(request.rentalIncome())
                .deductionProfessionalExpenses(request.deductionProfessionalExpenses())
                .deductionInsurancePremiums(request.deductionInsurancePremiums())
                .deductionCharitableDonations(request.deductionCharitableDonations())
                .deductionDebtInterest(request.deductionDebtInterest())
                .pillar3aContribution(request.pillar3aContribution())
                .netWealth(request.netWealth());

        // Merge semantics: filingDeadline and assessedAmount are maintained outside
        // the calculator form, so a recalculation payload that omits them must never
        // null out previously stored values.
        if (request.filingDeadline() != null) {
            builder.filingDeadline(request.filingDeadline());
        }
        if (request.assessedAmount() != null) {
            builder.assessedAmount(request.assessedAmount());
        }

        TaxYear saved = taxYearRepository.save(builder.build());
        return toDetail(saved, userId);
    }

    @Transactional
    public TaxYearDetailResponse updateStatus(int year, TaxYearStatusRequest request, String userId) {
        log.info("Updating status of tax year={} to {} for user={}", year, request.status(), userId);
        TaxYear taxYear = loadYear(year, userId);
        TaxYearStatus target = request.status();
        if (!taxYear.getStatus().canTransitionTo(target)) {
            throw new IllegalStateException(
                    "Cannot transition tax year " + year + " from " + taxYear.getStatus() + " to " + target);
        }

        LocalDate effectiveDate = request.effectiveDate() != null ? request.effectiveDate() : LocalDate.now();
        TaxYear.TaxYearBuilder builder = taxYear.toBuilder().status(target);

        if (target.compareTo(TaxYearStatus.FILED) >= 0) {
            if (taxYear.getFiledAt() == null) {
                builder.filedAt(effectiveDate);
            }
        } else {
            builder.filedAt(null);
        }
        if (target.compareTo(TaxYearStatus.ASSESSED) >= 0) {
            if (taxYear.getAssessedAt() == null) {
                builder.assessedAt(effectiveDate);
            }
        } else {
            builder.assessedAt(null);
        }

        return toDetail(taxYearRepository.save(builder.build()), userId);
    }

    @Transactional
    public void delete(int year, String userId) {
        log.info("Deleting tax year={} for user={}", year, userId);
        // payments and deadlines are removed by the ON DELETE CASCADE constraint
        taxYearRepository.delete(loadYear(year, userId));
    }

    // -------------------------------------------------------------------------
    // Payments
    // -------------------------------------------------------------------------

    @Transactional
    public TaxPaymentResponse addPayment(int year, TaxPaymentRequest request, String userId) {
        log.info("Adding payment to tax year={} for user={}", year, userId);
        TaxYear taxYear = loadYear(year, userId);
        TaxPayment saved = taxPaymentRepository.save(TaxPayment.builder()
                .taxYearId(taxYear.getId())
                .userId(userId)
                .paymentDate(request.paymentDate())
                .amount(request.amount())
                .label(request.label())
                .build());
        return TaxPaymentResponse.from(saved);
    }

    @Transactional
    public TaxPaymentResponse updatePayment(int year, UUID paymentId, TaxPaymentRequest request, String userId) {
        log.info("Updating payment id={} of tax year={} for user={}", paymentId, year, userId);
        TaxPayment existing = loadPayment(year, paymentId, userId);
        TaxPayment saved = taxPaymentRepository.save(existing.toBuilder()
                .paymentDate(request.paymentDate())
                .amount(request.amount())
                .label(request.label())
                .build());
        return TaxPaymentResponse.from(saved);
    }

    @Transactional
    public void deletePayment(int year, UUID paymentId, String userId) {
        log.info("Deleting payment id={} of tax year={} for user={}", paymentId, year, userId);
        taxPaymentRepository.delete(loadPayment(year, paymentId, userId));
    }

    // -------------------------------------------------------------------------
    // Deadlines
    // -------------------------------------------------------------------------

    @Transactional
    public TaxDeadlineResponse addDeadline(int year, TaxDeadlineRequest request, String userId) {
        log.info("Adding deadline to tax year={} for user={}", year, userId);
        TaxYear taxYear = loadYear(year, userId);
        TaxDeadline saved = taxDeadlineRepository.save(TaxDeadline.builder()
                .taxYearId(taxYear.getId())
                .userId(userId)
                .dueDate(request.dueDate())
                .label(request.label())
                .amount(request.amount())
                .done(request.done())
                .build());
        return TaxDeadlineResponse.from(saved);
    }

    @Transactional
    public TaxDeadlineResponse updateDeadline(int year, UUID deadlineId, TaxDeadlineRequest request, String userId) {
        log.info("Updating deadline id={} of tax year={} for user={}", deadlineId, year, userId);
        TaxDeadline existing = loadDeadline(year, deadlineId, userId);
        TaxDeadline saved = taxDeadlineRepository.save(existing.toBuilder()
                .dueDate(request.dueDate())
                .label(request.label())
                .amount(request.amount())
                .done(request.done())
                .build());
        return TaxDeadlineResponse.from(saved);
    }

    @Transactional
    public void deleteDeadline(int year, UUID deadlineId, String userId) {
        log.info("Deleting deadline id={} of tax year={} for user={}", deadlineId, year, userId);
        taxDeadlineRepository.delete(loadDeadline(year, deadlineId, userId));
    }

    // -------------------------------------------------------------------------
    // Loading with ownership checks — cross-user access surfaces as 404
    // -------------------------------------------------------------------------

    private TaxYear loadYear(int year, String userId) {
        return taxYearRepository.findByUserIdAndTaxYear(userId, year)
                .orElseThrow(() -> ResourceNotFoundException.of("Tax year", year));
    }

    private TaxPayment loadPayment(int year, UUID paymentId, String userId) {
        TaxYear taxYear = loadYear(year, userId);
        return taxPaymentRepository.findByIdAndUserId(paymentId, userId)
                .filter(payment -> payment.getTaxYearId().equals(taxYear.getId()))
                .orElseThrow(() -> ResourceNotFoundException.of("Tax payment", paymentId));
    }

    private TaxDeadline loadDeadline(int year, UUID deadlineId, String userId) {
        TaxYear taxYear = loadYear(year, userId);
        return taxDeadlineRepository.findByIdAndUserId(deadlineId, userId)
                .filter(deadline -> deadline.getTaxYearId().equals(taxYear.getId()))
                .orElseThrow(() -> ResourceNotFoundException.of("Tax deadline", deadlineId));
    }

    // -------------------------------------------------------------------------
    // Derived values — always recomputed, never cached
    // -------------------------------------------------------------------------

    private TaxYearSummaryResponse toSummary(TaxYear taxYear, String userId) {
        TaxResultResponse calculation = recompute(taxYear);
        BigDecimal paidTotal = paidTotal(taxPaymentRepository
                .findByTaxYearIdAndUserIdOrderByPaymentDateAsc(taxYear.getId(), userId));
        BigDecimal expectedTax = expectedTax(taxYear, calculation);
        return new TaxYearSummaryResponse(
                taxYear.getTaxYear(),
                taxYear.getStatus(),
                expectedTax,
                paidTotal,
                openAmount(expectedTax, paidTotal),
                calculation != null ? calculation.grossIncome() : null,
                calculation != null ? calculation.effectiveRatePercent() : null
        );
    }

    private TaxYearDetailResponse toDetail(TaxYear taxYear, String userId) {
        TaxResultResponse calculation = recompute(taxYear);
        BigDecimal expectedTax = expectedTax(taxYear, calculation);
        List<TaxPayment> paymentEntities = taxPaymentRepository
                .findByTaxYearIdAndUserIdOrderByPaymentDateAsc(taxYear.getId(), userId);
        BigDecimal paidTotal = paidTotal(paymentEntities);
        List<TaxPaymentResponse> payments = paymentEntities.stream()
                .map(TaxPaymentResponse::from).toList();
        List<TaxDeadlineResponse> deadlines = taxDeadlineRepository
                .findByTaxYearIdAndUserIdOrderByDueDateAsc(taxYear.getId(), userId)
                .stream().map(TaxDeadlineResponse::from).toList();
        return new TaxYearDetailResponse(
                taxYear.getTaxYear(),
                taxYear.getStatus(),
                TaxYearInputs.from(taxYear),
                calculation,
                payments,
                deadlines,
                expectedTax,
                paidTotal,
                openAmount(expectedTax, paidTotal),
                taxYear.getFilingDeadline(),
                taxYear.getFiledAt(),
                taxYear.getAssessedAt(),
                taxYear.getAssessedAmount()
        );
    }

    private TaxResultResponse recompute(TaxYear taxYear) {
        if (taxYear.getCantonCode() == null
                || taxYear.getCivilStatus() == null
                || taxYear.getGrossEmploymentIncome() == null) {
            return null;
        }
        return taxCalculationService.calculate(new TaxInputRequest(
                taxYear.getTaxYear(),
                taxYear.getCantonCode(),
                taxYear.getBfsNumber(),
                taxYear.getCivilStatus(),
                taxYear.getNumberOfChildren() != null ? taxYear.getNumberOfChildren() : 0,
                taxYear.getChurchAffiliation(),
                taxYear.getGrossEmploymentIncome(),
                taxYear.getSelfEmploymentIncome(),
                taxYear.getInvestmentIncome(),
                taxYear.getRentalIncome(),
                taxYear.getDeductionProfessionalExpenses(),
                taxYear.getDeductionInsurancePremiums(),
                taxYear.getDeductionCharitableDonations(),
                taxYear.getDeductionDebtInterest(),
                taxYear.getPillar3aContribution(),
                taxYear.getNetWealth()
        ));
    }

    private BigDecimal expectedTax(TaxYear taxYear, TaxResultResponse calculation) {
        boolean assessedOrPaid = taxYear.getStatus() == TaxYearStatus.ASSESSED
                || taxYear.getStatus() == TaxYearStatus.PAID;
        if (assessedOrPaid && taxYear.getAssessedAmount() != null) {
            return taxYear.getAssessedAmount();
        }
        return calculation != null ? calculation.grandTotal() : null;
    }

    private BigDecimal paidTotal(List<TaxPayment> payments) {
        return payments.stream()
                .map(TaxPayment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** May be negative — a refund is due when payments exceed the expected tax. */
    private BigDecimal openAmount(BigDecimal expectedTax, BigDecimal paidTotal) {
        return expectedTax != null ? expectedTax.subtract(paidTotal) : null;
    }
}
