import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { TaxResultSection } from './TaxResultSection';
import { taxResult, taxScenario, taxYearInputs } from '@/test/fixtures/tax';

describe('TaxResultSection', () => {
  it('shows the breakdown rows with formatted amounts', () => {
    render(<TaxResultSection result={taxResult()} inputs={taxYearInputs()} />);

    expect(screen.getByText('Tax Breakdown — Canton SG 2025')).toBeInTheDocument();
    expect(screen.getByText("CHF 100'000.00")).toBeInTheDocument(); // gross income
    expect(screen.getByText("CHF 90'000.00")).toBeInTheDocument(); // taxable income
    expect(screen.getByText("CHF 2'000.00")).toBeInTheDocument(); // federal
    expect(screen.getByText("CHF 6'000.00")).toBeInTheDocument(); // cantonal
    expect(screen.getByText("CHF 4'000.00")).toBeInTheDocument(); // communal
    expect(screen.getByText("CHF 12'000.00")).toBeInTheDocument(); // grand total
  });

  it('shows the effective and marginal rates', () => {
    render(<TaxResultSection result={taxResult()} inputs={taxYearInputs()} />);

    expect(screen.getByText('12.0%')).toBeInTheDocument();
    expect(screen.getByText('22.0%')).toBeInTheDocument();
  });

  it('hides church, wealth tax and pillar 3a rows when not applicable', () => {
    render(<TaxResultSection result={taxResult()} inputs={taxYearInputs()} />);

    expect(screen.queryByText('Church Tax')).not.toBeInTheDocument();
    expect(screen.queryByText('Wealth Tax')).not.toBeInTheDocument();
    expect(screen.queryByText('Pillar 3a Tax Saving')).not.toBeInTheDocument();
  });

  it('shows church, wealth tax and pillar 3a saving when present', () => {
    render(
      <TaxResultSection
        result={taxResult({ churchTax: 500, wealthTax: 300, pillar3aTaxSaving: 1500 })}
        inputs={taxYearInputs({ pillar3aContribution: 7258 })}
      />,
    );

    expect(screen.getAllByText('Church Tax').length).toBeGreaterThan(0);
    expect(screen.getByText('Wealth Tax')).toBeInTheDocument();
    expect(screen.getByText('Pillar 3a Tax Saving')).toBeInTheDocument();
    expect(screen.getByText("− CHF 1'500.00")).toBeInTheDocument();
  });

  it('expands the deduction details on click', async () => {
    const user = userEvent.setup();
    render(
      <TaxResultSection
        result={taxResult()}
        inputs={taxYearInputs({ pillar3aContribution: 7258, deductionProfessionalExpenses: 2400 })}
      />,
    );

    expect(screen.queryByText('Professional expenses')).not.toBeInTheDocument();

    await user.click(screen.getByText('Total Deductions'));

    expect(await screen.findByText('Professional expenses')).toBeInTheDocument();
    expect(screen.getByText("CHF 2'400.00")).toBeInTheDocument();
    expect(screen.getByText('Pillar 3a')).toBeInTheDocument();
  });

  it('opens the deductions and prints on PDF export', async () => {
    const printSpy = vi.fn();
    vi.stubGlobal('print', printSpy);
    vi.spyOn(globalThis, 'requestAnimationFrame').mockImplementation((cb) => {
      cb(0);
      return 0;
    });
    const user = userEvent.setup();
    render(
      <TaxResultSection
        result={taxResult()}
        inputs={taxYearInputs({ deductionProfessionalExpenses: 2400 })}
      />,
    );

    await user.click(screen.getByRole('button', { name: /Export as PDF/ }));

    expect(printSpy).toHaveBeenCalledTimes(1);
    expect(await screen.findByText('Professional expenses')).toBeInTheDocument();
    vi.unstubAllGlobals();
  });

  it('renders the selected scenario calculation instead of the year result', () => {
    render(
      <TaxResultSection
        result={taxResult({ grandTotal: 12_000 })}
        inputs={taxYearInputs()}
        scenario={taxScenario({
          id: 's2',
          name: 'Without 3a',
          calculation: taxResult({ grandTotal: 19_732, taxableIncome: 98_520 }),
        })}
      />,
    );

    expect(
      screen.getByText('Tax Breakdown — Canton SG 2025 · Without 3a'),
    ).toBeInTheDocument();
    expect(screen.getByText("CHF 19'732.00")).toBeInTheDocument();
    expect(screen.getByText("CHF 98'520.00")).toBeInTheDocument();
    expect(screen.queryByText("CHF 12'000.00")).not.toBeInTheDocument();
  });

  it('appends a star to the title when the selected scenario is the default', () => {
    const scenario = taxScenario({ id: 's1', name: 'Status quo', isDefault: true });
    render(
      <TaxResultSection
        result={taxResult()}
        inputs={taxYearInputs()}
        scenario={scenario}
        defaultScenario={scenario}
      />,
    );

    expect(
      screen.getByText('Tax Breakdown — Canton SG 2025 · Status quo ★'),
    ).toBeInTheDocument();
  });

  it('shows a positive delta badge when the scenario is more expensive than the default', () => {
    render(
      <TaxResultSection
        result={taxResult()}
        inputs={taxYearInputs()}
        scenario={taxScenario({
          id: 's2',
          name: 'Without 3a',
          calculation: taxResult({ grandTotal: 19_732 }),
        })}
        defaultScenario={taxScenario({
          id: 's1',
          isDefault: true,
          calculation: taxResult({ grandTotal: 17_842 }),
        })}
      />,
    );

    expect(screen.getByText("+CHF 1'890.00 vs. default")).toBeInTheDocument();
  });

  it('shows a negative delta badge when the scenario is cheaper than the default', () => {
    render(
      <TaxResultSection
        result={taxResult()}
        inputs={taxYearInputs()}
        scenario={taxScenario({
          id: 's2',
          name: 'Max 3a',
          calculation: taxResult({ grandTotal: 15_000 }),
        })}
        defaultScenario={taxScenario({
          id: 's1',
          isDefault: true,
          calculation: taxResult({ grandTotal: 17_842 }),
        })}
      />,
    );

    expect(screen.getByText("−CHF 2'842.00 vs. default")).toBeInTheDocument();
  });

  it('hides the delta badge when the selected scenario is the default', () => {
    const scenario = taxScenario({
      id: 's1',
      isDefault: true,
      calculation: taxResult({ grandTotal: 17_842 }),
    });
    render(
      <TaxResultSection
        result={taxResult()}
        inputs={taxYearInputs()}
        scenario={scenario}
        defaultScenario={scenario}
      />,
    );

    expect(screen.queryByText(/vs\. default/)).not.toBeInTheDocument();
  });

  it('hides the delta badge when no default scenario exists', () => {
    render(
      <TaxResultSection
        result={taxResult()}
        inputs={taxYearInputs()}
        scenario={taxScenario({ id: 's2', calculation: taxResult({ grandTotal: 19_732 }) })}
        defaultScenario={null}
      />,
    );

    expect(screen.queryByText(/vs\. default/)).not.toBeInTheDocument();
  });

  it('shows a hint instead of numbers for a scenario without a calculation', () => {
    render(
      <TaxResultSection
        result={taxResult()}
        inputs={taxYearInputs()}
        scenario={taxScenario({ id: 's3', name: 'Incomplete', calculation: null })}
      />,
    );

    expect(
      screen.getByText('This scenario has no calculation yet — the saved inputs are incomplete.'),
    ).toBeInTheDocument();
    expect(screen.queryByText('Grand Total')).not.toBeInTheDocument();
  });
});
