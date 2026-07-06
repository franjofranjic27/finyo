import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { DeadlinesPaymentsSection } from './DeadlinesPaymentsSection';
import { taxApi } from '@/api/tax';
import { taxYearDetail } from '@/test/fixtures/tax';
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
    addPayment: vi.fn(),
    deletePayment: vi.fn(),
    addDeadline: vi.fn(),
    updateDeadline: vi.fn(),
  },
}));

const detail = taxYearDetail({
  year: 2025,
  deadlines: [
    { id: 'd1', dueDate: '2025-03-31', label: 'File tax return', amount: null, done: false },
    { id: 'd2', dueDate: '2025-09-30', label: 'Final payment', amount: 4000, done: true },
  ],
  payments: [{ id: 'p1', paymentDate: '2025-02-01', amount: 1500, label: 'Q1 instalment' }],
});

const emptyDetail = taxYearDetail({ year: 2025 });

describe('DeadlinesPaymentsSection', () => {
  it('lists deadlines with due date, amount and status', () => {
    renderWithProviders(<DeadlinesPaymentsSection year={2025} detail={detail} />);

    expect(screen.getByText('File tax return')).toBeInTheDocument();
    expect(screen.getByText('Final payment')).toBeInTheDocument();
    expect(screen.getByText(/CHF 4'000\.00/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Open' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Done' })).toBeInTheDocument();
  });

  it('lists payments with the formatted amount', () => {
    renderWithProviders(<DeadlinesPaymentsSection year={2025} detail={detail} />);

    expect(screen.getByText('Q1 instalment')).toBeInTheDocument();
    expect(screen.getByText("CHF 1'500.00")).toBeInTheDocument();
  });

  it('shows empty states without deadlines and payments', () => {
    renderWithProviders(<DeadlinesPaymentsSection year={2025} detail={emptyDetail} />);

    expect(screen.getByText('No deadlines')).toBeInTheDocument();
    expect(screen.getByText('No payments recorded yet')).toBeInTheDocument();
  });

  it('toggles a deadline to done', async () => {
    vi.mocked(taxApi.updateDeadline).mockResolvedValue({
      ...detail.deadlines[0],
      done: true,
    });
    const user = userEvent.setup();
    renderWithProviders(<DeadlinesPaymentsSection year={2025} detail={detail} />);

    await user.click(screen.getByRole('button', { name: 'Open' }));

    await waitFor(() =>
      expect(taxApi.updateDeadline).toHaveBeenCalledWith('test-token', 2025, 'd1', {
        dueDate: '2025-03-31',
        label: 'File tax return',
        amount: null,
        done: true,
      }),
    );
  });

  it('adds a payment through the dialog', async () => {
    vi.mocked(taxApi.addPayment).mockResolvedValue({
      id: 'p2',
      paymentDate: '2025-05-01',
      amount: 2000,
      label: 'Q2',
    });
    const user = userEvent.setup();
    renderWithProviders(<DeadlinesPaymentsSection year={2025} detail={detail} />);

    await user.click(screen.getByRole('button', { name: /Add Payment/ }));
    await user.type(screen.getByLabelText('Amount (CHF)'), '2000');
    await user.type(screen.getByLabelText('Label'), 'Q2');
    await user.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(taxApi.addPayment).toHaveBeenCalledTimes(1));
    const [token, year, payment] = vi.mocked(taxApi.addPayment).mock.calls[0];
    expect(token).toBe('test-token');
    expect(year).toBe(2025);
    expect(payment).toMatchObject({ amount: 2000, label: 'Q2' });
  });

  it('adds a deadline through the dialog', async () => {
    vi.mocked(taxApi.addDeadline).mockResolvedValue({
      id: 'd3',
      dueDate: '2025-11-30',
      label: 'Provisional invoice',
      amount: null,
      done: false,
    });
    const user = userEvent.setup();
    renderWithProviders(<DeadlinesPaymentsSection year={2025} detail={detail} />);

    await user.click(screen.getByRole('button', { name: /Add deadline/ }));
    await user.type(screen.getByLabelText('Description'), 'Provisional invoice');
    await user.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(taxApi.addDeadline).toHaveBeenCalledTimes(1));
    const [, , deadline] = vi.mocked(taxApi.addDeadline).mock.calls[0];
    expect(deadline).toMatchObject({ label: 'Provisional invoice', amount: null, done: false });
  });

  it('deletes a payment after confirmation', async () => {
    vi.mocked(taxApi.deletePayment).mockResolvedValue(undefined);
    vi.spyOn(globalThis, 'confirm').mockReturnValue(true);
    const user = userEvent.setup();
    renderWithProviders(<DeadlinesPaymentsSection year={2025} detail={detail} />);

    await user.click(screen.getByRole('button', { name: 'Delete' }));

    await waitFor(() =>
      expect(taxApi.deletePayment).toHaveBeenCalledWith('test-token', 2025, 'p1'),
    );
  });
});
