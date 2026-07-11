import { describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ScenarioBar } from './ScenarioBar';
import { taxResult, taxScenario, taxYearInputs } from '@/test/fixtures/tax';

const scenarios = [
  taxScenario({
    id: 's1',
    name: 'Status quo',
    isDefault: true,
    inputs: taxYearInputs({ pillar3aContribution: 7258 }),
    calculation: taxResult({ grandTotal: 17_842 }),
  }),
  taxScenario({
    id: 's2',
    name: 'Without 3a',
    inputs: taxYearInputs({ pillar3aContribution: null }),
    calculation: taxResult({ grandTotal: 19_732 }),
  }),
  taxScenario({ id: 's3', name: 'Incomplete', calculation: null }),
  taxScenario({
    id: 's4',
    name: 'Zero 3a',
    inputs: taxYearInputs({ pillar3aContribution: 0 }),
    calculation: taxResult({ grandTotal: 20_000 }),
  }),
];

describe('ScenarioBar', () => {
  it('renders one chip per scenario with a star on the default only', () => {
    render(
      <ScenarioBar
        scenarios={scenarios}
        selectedScenarioId={null}
        onSelect={vi.fn()}
        onAdd={vi.fn()}
      />,
    );

    const defaultChip = screen.getByRole('button', { name: /Status quo/ });
    const otherChip = screen.getByRole('button', { name: /Without 3a/ });
    expect(within(defaultChip).getByText('★')).toBeInTheDocument();
    expect(within(otherChip).queryByText('★')).not.toBeInTheDocument();
  });

  it('shows the 3a metric only for chips with a numeric contribution', () => {
    render(
      <ScenarioBar
        scenarios={scenarios}
        selectedScenarioId={null}
        onSelect={vi.fn()}
        onAdd={vi.fn()}
      />,
    );

    expect(screen.getByText("3a 7'258")).toBeInTheDocument();
    // Explicit 0 renders, a null contribution omits the metric entirely
    expect(screen.getByText('3a 0')).toBeInTheDocument();
    const nullChip = screen.getByRole('button', { name: /Without 3a/ });
    expect(within(nullChip).queryByText(/^3a/)).not.toBeInTheDocument();
    const incompleteChip = screen.getByRole('button', { name: /Incomplete/ });
    expect(within(incompleteChip).queryByText(/^3a/)).not.toBeInTheDocument();
  });

  it('exposes the chip bar as a labelled group', () => {
    render(
      <ScenarioBar
        scenarios={scenarios}
        selectedScenarioId={null}
        onSelect={vi.fn()}
        onAdd={vi.fn()}
      />,
    );

    expect(screen.getByRole('group', { name: 'Scenarios' })).toBeInTheDocument();
  });

  it('marks the selected chip as pressed', () => {
    render(
      <ScenarioBar
        scenarios={scenarios}
        selectedScenarioId="s2"
        onSelect={vi.fn()}
        onAdd={vi.fn()}
      />,
    );

    expect(screen.getByRole('button', { name: /Without 3a/ })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    expect(screen.getByRole('button', { name: /Status quo/ })).toHaveAttribute(
      'aria-pressed',
      'false',
    );
  });

  it('selects a chip and deselects the active one on a second click', async () => {
    const onSelect = vi.fn();
    const user = userEvent.setup();
    render(
      <ScenarioBar
        scenarios={scenarios}
        selectedScenarioId="s1"
        onSelect={onSelect}
        onAdd={vi.fn()}
      />,
    );

    await user.click(screen.getByRole('button', { name: /Without 3a/ }));
    expect(onSelect).toHaveBeenLastCalledWith('s2');

    await user.click(screen.getByRole('button', { name: /Status quo/ }));
    expect(onSelect).toHaveBeenLastCalledWith(null);
  });

  it('triggers the add flow via the dashed chip and respects addDisabled', async () => {
    const onAdd = vi.fn();
    const user = userEvent.setup();
    const { rerender } = render(
      <ScenarioBar scenarios={[]} selectedScenarioId={null} onSelect={vi.fn()} onAdd={onAdd} />,
    );

    await user.click(screen.getByRole('button', { name: /New scenario/ }));
    expect(onAdd).toHaveBeenCalledTimes(1);

    rerender(
      <ScenarioBar
        scenarios={[]}
        selectedScenarioId={null}
        onSelect={vi.fn()}
        onAdd={onAdd}
        addDisabled
      />,
    );
    expect(screen.getByRole('button', { name: /New scenario/ })).toBeDisabled();
  });
});
