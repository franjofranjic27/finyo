import { test, expect } from '@playwright/test';

test.describe('Dashboard', () => {
  test('shows main navigation items', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByText('Dashboard')).toBeVisible();
    await expect(page.getByText('Transactions')).toBeVisible();
    await expect(page.getByText('Budget')).toBeVisible();
  });

  test('can toggle dark mode', async ({ page }) => {
    await page.goto('/dashboard');
    const html = page.locator('html');
    const darkToggle = page.getByRole('button', { name: /dark|light/i });
    if (await darkToggle.count() > 0) {
      await darkToggle.click();
      await expect(html).toHaveClass(/dark/);
    }
  });
});
