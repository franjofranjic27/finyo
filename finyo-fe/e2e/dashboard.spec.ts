import { test, expect } from '@playwright/test';

// NOTE: the app now enforces a Keycloak login. Without a running Keycloak
// and a test session, unauthenticated visitors never see the app shell —
// these tests assert the auth gate instead of the dashboard content.
test.describe('Auth gate', () => {
  test('unauthenticated visitor does not see the app shell', async ({ page }) => {
    await page.goto('/dashboard');
    // Either the Keycloak login page (different origin) or the transitional
    // "signing you in" screen — never the sidebar navigation.
    await expect(page.getByText('Investments')).toHaveCount(0);
    await expect(page.getByText('Insurance')).toHaveCount(0);
  });
});
