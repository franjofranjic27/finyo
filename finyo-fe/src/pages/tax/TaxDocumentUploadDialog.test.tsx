import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { TaxDocumentUploadDialog } from './TaxDocumentUploadDialog';
import { taxApi } from '@/api/tax';
import { classifyDocument, extractDocument, TaxDocumentError } from '@/api/taxDocuments';
import type { DocumentExtraction } from '@/api/taxDocuments';
import { taxYearDetail, taxYearInputs } from '@/test/fixtures/tax';
import { renderWithProviders } from '@/test/test-utils';

vi.mock('@/auth/useAuth', () => ({
  useAuth: () => ({
    isAuthenticated: true,
    isLoading: false,
    user: { profile: {} },
    accessToken: 'test-token',
    roles: ['user'],
    hasRole: () => true,
    login: vi.fn(),
    logout: vi.fn(),
  }),
}));

vi.mock('@/api/tax', () => ({
  taxApi: {
    upsertYear: vi.fn(),
  },
}));

// Keep TaxDocumentError real so the dialog's instanceof branching works.
vi.mock('@/api/taxDocuments', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/taxDocuments')>();
  return {
    ...actual,
    classifyDocument: vi.fn(),
    extractDocument: vi.fn(),
  };
});

const inputs = taxYearInputs();

const salaryExtraction: DocumentExtraction = {
  type: 'SALARY_CERTIFICATE',
  taxYear: 2025,
  fields: {
    grossSalary: 52_592,
    socialSecurityContributions: 3350.5,
    pensionContributions: 2900,
    netSalary: 46_341.5,
  },
  taxYearMapping: [
    {
      fieldName: 'grossSalary',
      label: 'Bruttolohn total (Ziffer 8)',
      value: 52_592,
      targetTaxYearField: 'grossEmploymentIncome',
    },
  ],
};

const securitiesExtraction: DocumentExtraction = {
  type: 'SECURITIES_STATEMENT',
  taxYear: 2025,
  fields: { totalTaxValue: 80_000, totalGrossIncome: 1200, totalFees: 150 },
  taxYearMapping: [
    {
      fieldName: 'totalTaxValue',
      label: 'Steuerwert total',
      value: 80_000,
      targetTaxYearField: 'netWealth',
    },
    {
      fieldName: 'totalGrossIncome',
      label: 'Bruttoertrag total',
      value: 1200,
      targetTaxYearField: 'investmentIncome',
    },
  ],
};

const pillar3aExtraction: DocumentExtraction = {
  type: 'PILLAR_3A',
  taxYear: 2025,
  fields: { contribution: 7056, institution: 'VIAC' },
  taxYearMapping: [
    {
      fieldName: 'contribution',
      label: 'Einzahlung Säule 3a',
      value: 7056,
      targetTaxYearField: 'pillar3aContribution',
    },
  ],
};

function pdfFile(name = 'dokument.pdf'): File {
  return new File(['%PDF-1.4'], name, { type: 'application/pdf' });
}

function renderDialog() {
  const onOpenChange = vi.fn();
  const result = renderWithProviders(
    <TaxDocumentUploadDialog
      year={2025}
      inputs={inputs}
      assessedAmount={null}
      open
      onOpenChange={onOpenChange}
    />,
  );
  return { ...result, onOpenChange };
}

async function uploadPdf(user: ReturnType<typeof userEvent.setup>, file = pdfFile()) {
  await user.upload(screen.getByLabelText('or choose a file'), file);
  return file;
}

describe('TaxDocumentUploadDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('classifies, extracts and applies the merged salary values on confirm', async () => {
    vi.mocked(classifyDocument).mockResolvedValue({
      detectedType: 'SALARY_CERTIFICATE',
      confidence: 0.93,
    });
    vi.mocked(extractDocument).mockResolvedValue(salaryExtraction);
    vi.mocked(taxApi.upsertYear).mockResolvedValue(taxYearDetail({ year: 2025 }));
    const user = userEvent.setup();
    const { onOpenChange } = renderDialog();

    const file = await uploadPdf(user, pdfFile('lohnausweis.pdf'));

    // Extracted vs. current value in the preview table
    expect(await screen.findByText("CHF 52'592.00")).toBeInTheDocument();
    expect(screen.getByText("CHF 100'000.00")).toBeInTheDocument();
    expect(screen.getByText('Bruttolohn total (Ziffer 8)')).toBeInTheDocument();
    expect(classifyDocument).toHaveBeenCalledWith('test-token', file);
    expect(extractDocument).toHaveBeenCalledWith('test-token', 'SALARY_CERTIFICATE', file);
    expect(screen.getByText('93% confidence')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Apply 1 value' }));

    await waitFor(() =>
      expect(taxApi.upsertYear).toHaveBeenCalledWith('test-token', 2025, {
        ...inputs,
        grossEmploymentIncome: 52_592,
      }),
    );
    expect(onOpenChange).toHaveBeenCalledWith(false);
  });

  it('excludes unchecked rows from the payload and disables apply at zero', async () => {
    vi.mocked(classifyDocument).mockResolvedValue({
      detectedType: 'SECURITIES_STATEMENT',
      confidence: 0.88,
    });
    vi.mocked(extractDocument).mockResolvedValue(securitiesExtraction);
    vi.mocked(taxApi.upsertYear).mockResolvedValue(taxYearDetail({ year: 2025 }));
    const user = userEvent.setup();
    renderDialog();

    await uploadPdf(user);
    expect(await screen.findByText("CHF 80'000.00")).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Apply 2 values' })).toBeEnabled();

    // Unchecking everything disables the confirm button
    await user.click(screen.getByRole('checkbox', { name: 'Steuerwert total' }));
    await user.click(screen.getByRole('checkbox', { name: 'Bruttoertrag total' }));
    expect(screen.getByRole('button', { name: 'Apply 0 values' })).toBeDisabled();

    // Re-check only the tax value: the income mapping must stay untouched
    await user.click(screen.getByRole('checkbox', { name: 'Steuerwert total' }));
    await user.click(screen.getByRole('button', { name: 'Apply 1 value' }));

    await waitFor(() =>
      expect(taxApi.upsertYear).toHaveBeenCalledWith('test-token', 2025, {
        ...inputs,
        netWealth: 80_000,
      }),
    );
  });

  it('shows the backend detail message for a 422 scan error', async () => {
    vi.mocked(classifyDocument).mockRejectedValue(
      new TaxDocumentError('Das PDF enthält keinen auswertbaren Text.', 422),
    );
    const user = userEvent.setup();
    renderDialog();

    await uploadPdf(user);

    expect(
      await screen.findByText('Das PDF enthält keinen auswertbaren Text.'),
    ).toBeInTheDocument();
    expect(extractDocument).not.toHaveBeenCalled();
  });

  it('offers re-extraction with the detected type on a wrong-type 422', async () => {
    vi.mocked(classifyDocument).mockResolvedValue({ detectedType: 'UNKNOWN', confidence: 0.2 });
    vi.mocked(extractDocument)
      .mockRejectedValueOnce(
        new TaxDocumentError('Dies ist ein Lohnausweis.', 422, 'SALARY_CERTIFICATE'),
      )
      .mockResolvedValueOnce(salaryExtraction);
    const user = userEvent.setup();
    renderDialog();

    const file = await uploadPdf(user);
    await user.click(
      await screen.findByRole('button', { name: /Health insurance premium statement/ }),
    );

    expect(await screen.findByText('Wrong document type')).toBeInTheDocument();
    expect(screen.getByText('Dies ist ein Lohnausweis.')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Process as Salary certificate' }));

    expect(await screen.findByText("CHF 52'592.00")).toBeInTheDocument();
    expect(extractDocument).toHaveBeenLastCalledWith('test-token', 'SALARY_CERTIFICATE', file);
  });

  it('lets the user pick a type manually when classification is UNKNOWN', async () => {
    vi.mocked(classifyDocument).mockResolvedValue({ detectedType: 'UNKNOWN', confidence: 0.1 });
    vi.mocked(extractDocument).mockResolvedValue(pillar3aExtraction);
    const user = userEvent.setup();
    renderDialog();

    const file = await uploadPdf(user);

    expect(
      await screen.findByText('The document type could not be detected — pick it manually below.'),
    ).toBeInTheDocument();
    expect(extractDocument).not.toHaveBeenCalled();

    await user.click(screen.getByRole('button', { name: /Pillar 3a statement/ }));

    expect(await screen.findByText("CHF 7'056.00")).toBeInTheDocument();
    expect(extractDocument).toHaveBeenCalledWith('test-token', 'PILLAR_3A', file);
  });

  it('renders unmapped fields as info-only rows without a checkbox', async () => {
    vi.mocked(classifyDocument).mockResolvedValue({
      detectedType: 'SALARY_CERTIFICATE',
      confidence: 0.93,
    });
    vi.mocked(extractDocument).mockResolvedValue(salaryExtraction);
    const user = userEvent.setup();
    renderDialog();

    await uploadPdf(user);
    await screen.findByText("CHF 52'592.00");

    // Only the mapped row has a checkbox; the three extra salary fields are info-only.
    expect(screen.getAllByRole('checkbox')).toHaveLength(1);
    expect(screen.getAllByText('info only')).toHaveLength(3);
    expect(screen.getByText('Net salary')).toBeInTheDocument();
    expect(screen.getByText("CHF 46'341.50")).toBeInTheDocument();
  });
});
