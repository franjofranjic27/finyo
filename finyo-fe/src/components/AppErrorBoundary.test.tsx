import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AppErrorBoundary } from './AppErrorBoundary';
import '@/i18n/index';

function Bomb(): never {
  throw new Error('boom');
}

describe('AppErrorBoundary', () => {
  it('renders the children when nothing throws', () => {
    render(
      <AppErrorBoundary>
        <p>content</p>
      </AppErrorBoundary>
    );

    expect(screen.getByText('content')).toBeInTheDocument();
  });

  it('shows the reload screen instead of a blank page when a child throws', async () => {
    // React logs the caught error loudly; keep the test output clean.
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {});
    const user = userEvent.setup();
    const reload = vi.fn();
    vi.stubGlobal('location', { ...window.location, reload });

    render(
      <AppErrorBoundary>
        <Bomb />
      </AppErrorBoundary>
    );

    expect(screen.getByText('Something went wrong')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Reload' }));
    expect(reload).toHaveBeenCalled();

    vi.unstubAllGlobals();
    consoleError.mockRestore();
  });
});
