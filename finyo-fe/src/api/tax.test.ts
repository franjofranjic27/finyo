import { beforeEach, describe, expect, it, vi } from 'vitest';
import { taxApi } from './tax';
import type { TaxCalculationRequest, TaxDeadlineInput, TaxPaymentInput, TaxYearInputs } from './tax';
import { apiRequest } from './client';

vi.mock('./client', () => ({ apiRequest: vi.fn() }));

const apiRequestMock = vi.mocked(apiRequest);
const TOKEN = 'test-token';

const calculationRequest: TaxCalculationRequest = {
  taxYear: 2025,
  cantonCode: 'SG',
  civilStatus: 'SINGLE',
  numberOfChildren: 0,
  grossEmploymentIncome: 100_000,
};

const yearInputs: TaxYearInputs = {
  cantonCode: 'SG',
  bfsNumber: 3203,
  civilStatus: 'SINGLE',
  churchAffiliation: 'NONE',
  numberOfChildren: 0,
  grossEmploymentIncome: 100_000,
  selfEmploymentIncome: null,
  investmentIncome: null,
  rentalIncome: null,
  deductionProfessionalExpenses: null,
  deductionInsurancePremiums: null,
  deductionCharitableDonations: null,
  deductionDebtInterest: null,
  pillar3aContribution: 7258,
  netWealth: null,
};

describe('taxApi', () => {
  beforeEach(() => {
    apiRequestMock.mockReset();
  });

  it('calculate POSTs the calculation request', () => {
    taxApi.calculate(TOKEN, calculationRequest);
    expect(apiRequestMock).toHaveBeenCalledWith(
      '/tax/calculate',
      { method: 'POST', body: JSON.stringify(calculationRequest) },
      TOKEN,
    );
  });

  it('getCommunes builds the canton/year query with a 2025 default', () => {
    taxApi.getCommunes(TOKEN, 'SG');
    expect(apiRequestMock).toHaveBeenCalledWith('/tax/communes?canton=SG&year=2025', {}, TOKEN);

    taxApi.getCommunes(TOKEN, 'ZH', 2024);
    expect(apiRequestMock).toHaveBeenCalledWith('/tax/communes?canton=ZH&year=2024', {}, TOKEN);
  });

  it('calculatePillar3 POSTs to the pillar3 calculate endpoint', () => {
    const request = {
      currentBalance: 10_000,
      annualContribution: 7258,
      assumedAnnualReturnPercent: 5,
      yearsToRetirement: 30,
    };
    taxApi.calculatePillar3(TOKEN, request);
    expect(apiRequestMock).toHaveBeenCalledWith(
      '/tax/pillar3/calculate',
      { method: 'POST', body: JSON.stringify(request) },
      TOKEN,
    );
  });

  it('getYears requests the year summaries', () => {
    taxApi.getYears(TOKEN);
    expect(apiRequestMock).toHaveBeenCalledWith('/tax/years', {}, TOKEN);
  });

  it('getYear requests one year detail', () => {
    taxApi.getYear(TOKEN, 2024);
    expect(apiRequestMock).toHaveBeenCalledWith('/tax/years/2024', {}, TOKEN);
  });

  it('upsertYear PUTs the inputs to the year path', () => {
    taxApi.upsertYear(TOKEN, 2025, yearInputs);
    expect(apiRequestMock).toHaveBeenCalledWith(
      '/tax/years/2025',
      { method: 'PUT', body: JSON.stringify(yearInputs) },
      TOKEN,
    );
  });

  it('updateYearStatus PATCHes status with a null effective date by default', () => {
    taxApi.updateYearStatus(TOKEN, 2024, 'FILED');
    expect(apiRequestMock).toHaveBeenCalledWith(
      '/tax/years/2024/status',
      { method: 'PATCH', body: JSON.stringify({ status: 'FILED', effectiveDate: null }) },
      TOKEN,
    );
  });

  it('updateYearStatus passes an explicit effective date', () => {
    taxApi.updateYearStatus(TOKEN, 2024, 'PAID', '2025-03-01');
    expect(apiRequestMock).toHaveBeenCalledWith(
      '/tax/years/2024/status',
      { method: 'PATCH', body: JSON.stringify({ status: 'PAID', effectiveDate: '2025-03-01' }) },
      TOKEN,
    );
  });

  it('deleteYear issues DELETE on the year path', () => {
    taxApi.deleteYear(TOKEN, 2024);
    expect(apiRequestMock).toHaveBeenCalledWith('/tax/years/2024', { method: 'DELETE' }, TOKEN);
  });

  it('addPayment POSTs the payment to the year', () => {
    const payment: TaxPaymentInput = { paymentDate: '2025-02-01', amount: 1500, label: 'Q1' };
    taxApi.addPayment(TOKEN, 2025, payment);
    expect(apiRequestMock).toHaveBeenCalledWith(
      '/tax/years/2025/payments',
      { method: 'POST', body: JSON.stringify(payment) },
      TOKEN,
    );
  });

  it('deletePayment issues DELETE on the payment path', () => {
    taxApi.deletePayment(TOKEN, 2025, 'p1');
    expect(apiRequestMock).toHaveBeenCalledWith(
      '/tax/years/2025/payments/p1',
      { method: 'DELETE' },
      TOKEN,
    );
  });

  it('addDeadline POSTs the deadline to the year', () => {
    const deadline: TaxDeadlineInput = {
      dueDate: '2025-03-31',
      label: 'Filing',
      amount: null,
      done: false,
    };
    taxApi.addDeadline(TOKEN, 2025, deadline);
    expect(apiRequestMock).toHaveBeenCalledWith(
      '/tax/years/2025/deadlines',
      { method: 'POST', body: JSON.stringify(deadline) },
      TOKEN,
    );
  });

  it('updateDeadline PUTs the deadline to its id path', () => {
    const deadline: TaxDeadlineInput = {
      dueDate: '2025-03-31',
      label: 'Filing',
      amount: 200,
      done: true,
    };
    taxApi.updateDeadline(TOKEN, 2025, 'd1', deadline);
    expect(apiRequestMock).toHaveBeenCalledWith(
      '/tax/years/2025/deadlines/d1',
      { method: 'PUT', body: JSON.stringify(deadline) },
      TOKEN,
    );
  });

  it('deleteDeadline issues DELETE on the deadline path', () => {
    taxApi.deleteDeadline(TOKEN, 2025, 'd1');
    expect(apiRequestMock).toHaveBeenCalledWith(
      '/tax/years/2025/deadlines/d1',
      { method: 'DELETE' },
      TOKEN,
    );
  });
});
