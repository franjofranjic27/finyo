import { afterEach, describe, expect, it } from 'vitest';
import i18n from './index';

describe('i18n', () => {
  afterEach(async () => {
    await i18n.changeLanguage('en');
  });

  it('is initialised with English and German resources', () => {
    expect(i18n.hasResourceBundle('en', 'translation')).toBe(true);
    expect(i18n.hasResourceBundle('de', 'translation')).toBe(true);
  });

  it('translates keys in English', () => {
    expect(i18n.t('nav.investments')).toBe('Investments');
  });

  it('translates keys in German after a language change', async () => {
    await i18n.changeLanguage('de');
    expect(i18n.t('nav.investments')).toBe('Investitionen');
  });

  it('falls back to English for unsupported languages', async () => {
    await i18n.changeLanguage('fr');
    expect(i18n.t('nav.investments')).toBe('Investments');
  });

  it('interpolates values', () => {
    expect(i18n.t('tax.calculateForYear', { year: 2025 })).toBe('Calculate for 2025');
  });
});
