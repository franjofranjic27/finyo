import { describe, expect, it } from 'vitest';
import { parseAccountsCsv } from './accountsCsv';

describe('parseAccountsCsv', () => {
  it('parses semicolon-delimited rows and skips the labelled header', () => {
    const text = [
      'Name;Typ;Bereich;Währung;IBAN;BIC;Vertragsnummer;Gebühren;Auflösen',
      'Privatkonto;Kontokorrent;Privat;CHF;CH93 0076 2011 6238 5295 7;RAIFCH22;81375-0005;CHF 5 / Monat;',
      'Sparkonto;Sparkonto;;;;;;;ja',
    ].join('\n');

    expect(parseAccountsCsv(text)).toEqual([
      {
        name: 'Privatkonto',
        type: 'CHECKING',
        scope: 'PRIVATE',
        currency: 'CHF',
        iban: 'CH93 0076 2011 6238 5295 7',
        bic: 'RAIFCH22',
        contractNumber: '81375-0005',
        feeNote: 'CHF 5 / Monat',
        toClose: undefined,
      },
      {
        name: 'Sparkonto',
        type: 'SAVINGS',
        scope: undefined,
        currency: 'CHF',
        iban: undefined,
        bic: undefined,
        contractNumber: undefined,
        feeNote: undefined,
        toClose: true,
      },
    ]);
  });

  it.each([
    ['Kontokorrent', 'CHECKING'],
    ['Girokonto', 'CHECKING'],
    ['Privatkonto', 'CHECKING'],
    ['checking', 'CHECKING'],
    ['Sparkonto', 'SAVINGS'],
    ['Sparen', 'SAVINGS'],
    ['savings', 'SAVINGS'],
    ['Kreditkarte', 'CREDIT_CARD'],
    ['Credit Card', 'CREDIT_CARD'],
    ['Depot', 'INVESTMENT'],
    ['Investment', 'INVESTMENT'],
    ['Broker', 'INVESTMENT'],
    ['Bargeld', 'CASH'],
    ['Cash', 'CASH'],
    ['Vorsorge', 'VORSORGE'],
    ['Vorsorgekonto', 'VORSORGE'],
    ['3a', 'VORSORGE'],
    ['Säule 3a', 'VORSORGE'],
    ['Sonstiges', 'OTHER'],
    ['Other', 'OTHER'],
  ])('maps the type synonym "%s" to %s', (typ, expected) => {
    expect(parseAccountsCsv(`Konto;${typ}`)).toEqual([
      expect.objectContaining({ name: 'Konto', type: expected }),
    ]);
  });

  it.each([
    ['Privat', 'PRIVATE'],
    ['privat', 'PRIVATE'],
    ['Geschäft', 'BUSINESS'],
    ['geschaeft', 'BUSINESS'],
    ['Business', 'BUSINESS'],
  ])('maps the scope "%s" to %s', (scope, expected) => {
    expect(parseAccountsCsv(`Konto;Sparkonto;${scope}`)).toEqual([
      expect.objectContaining({ scope: expected }),
    ]);
  });

  it('omits the scope when the column is empty or unknown', () => {
    expect(parseAccountsCsv('Konto;Sparkonto;;CHF')[0].scope).toBeUndefined();
    expect(parseAccountsCsv('Konto;Sparkonto;Verein;CHF')[0].scope).toBeUndefined();
  });

  it.each(['ja', 'JA', 'x', 'X', 'true', 'TRUE', '1'])(
    'sets toClose for the flag "%s"',
    (flag) => {
      expect(parseAccountsCsv(`Konto;Sparkonto;;;;;;;${flag}`)[0].toClose).toBe(true);
    },
  );

  it('leaves toClose unset for other flag values', () => {
    expect(parseAccountsCsv('Konto;Sparkonto;;;;;;;nein')[0].toClose).toBeUndefined();
    expect(parseAccountsCsv('Konto;Sparkonto')[0].toClose).toBeUndefined();
  });

  it('passes the IBAN through with spaces — the backend normalizes it', () => {
    expect(parseAccountsCsv('Konto;Sparkonto;;;ch93 0076 2011 6238 5295 7')[0].iban).toBe(
      'ch93 0076 2011 6238 5295 7',
    );
  });

  it('uppercases the currency and defaults an empty one to CHF', () => {
    expect(parseAccountsCsv('Konto;Sparkonto;;eur')[0].currency).toBe('EUR');
    expect(parseAccountsCsv('Konto;Sparkonto')[0].currency).toBe('CHF');
  });

  it('drops rows with a malformed currency to protect the batch', () => {
    expect(parseAccountsCsv('Konto;Sparkonto;;Franken')).toEqual([]);
  });

  it('skips invalid rows and keeps the valid ones', () => {
    const text = [
      'Privatkonto;Kontokorrent',
      ';Sparkonto',
      'Ohne Typ;',
      'Unbekannt;Wertschriften',
      'Nur Name',
      'Sparkonto;Sparen',
    ].join('\n');

    expect(parseAccountsCsv(text)).toEqual([
      expect.objectContaining({ name: 'Privatkonto', type: 'CHECKING' }),
      expect.objectContaining({ name: 'Sparkonto', type: 'SAVINGS' }),
    ]);
  });

  it('skips blank lines', () => {
    expect(parseAccountsCsv('\nPrivatkonto;Kontokorrent\n\n')).toHaveLength(1);
  });

  it('returns nothing for an empty file', () => {
    expect(parseAccountsCsv('')).toEqual([]);
  });
});
