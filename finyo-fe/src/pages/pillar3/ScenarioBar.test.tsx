import { describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ScenarioBar } from './ScenarioBar';
import { pillar3Product, pillar3Scenario } from '@/test/fixtures/pillar3';

const scenarios = [
  pillar3Scenario({
    id: 's1',
    name: 'Status quo',
    isDefault: true,
    product: pillar3Product({ id: 'p1', name: 'Alpha Fund' }),
    effectiveReturnPercent: 3.0,
  }),
  pillar3Scenario({
    id: 's2',
    name: 'Aggressive',
    product: null,
    effectiveReturnPercent: 6.5,
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
    const otherChip = screen.getByRole('button', { name: /Aggressive/ });
    expect(within(defaultChip).getByText('★')).toBeInTheDocument();
    expect(within(otherChip).queryByText('★')).not.toBeInTheDocument();
  });

  it('shows the product name or the manual return rate as the chip metric', () => {
    render(
      <ScenarioBar
        scenarios={scenarios}
        selectedScenarioId={null}
        onSelect={vi.fn()}
        onAdd={vi.fn()}
      />,
    );

    const productChip = screen.getByRole('button', { name: /Status quo/ });
    expect(within(productChip).getByText('Alpha Fund')).toBeInTheDocument();
    const manualChip = screen.getByRole('button', { name: /Aggressive/ });
    expect(within(manualChip).getByText('6.5 %')).toBeInTheDocument();
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

    expect(screen.getByRole('button', { name: /Aggressive/ })).toHaveAttribute(
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

    await user.click(screen.getByRole('button', { name: /Aggressive/ }));
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
