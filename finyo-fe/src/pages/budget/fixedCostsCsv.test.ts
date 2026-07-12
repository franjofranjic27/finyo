import { describe, expect, it } from 'vitest';
import { parseFixedCostsCsv } from './fixedCostsCsv';

describe('parseFixedCostsCsv', () => {
  it('parses semicolon-delimited rows and skips the labelled header', () => {
    const text = [
      'Bezeichnung;Kategorie;Zahlart;Betrag',
      'Krankenkasse;Versicherung;monatl.;205,50',
      'ActiveFitness;Sport;jährl.;699',
    ].join('\n');

    expect(parseFixedCostsCsv(text)).toEqual([
      { name: 'Krankenkasse', category: 'Versicherung', paymentInterval: 'MONTHLY', amount: 205.5 },
      { name: 'ActiveFitness', category: 'Sport', paymentInterval: 'YEARLY', amount: 699 },
    ]);
  });

  it('skips a header without a label when its amount column is not numeric', () => {
    const text = ['Abo;Rubrik;Zahlweise;Betrag', 'Netflix;;monthly;18.90'].join('\n');

    expect(parseFixedCostsCsv(text)).toEqual([
      { name: 'Netflix', category: undefined, paymentInterval: 'MONTHLY', amount: 18.9 },
    ]);
  });

  it('parses comma-delimited rows without a header', () => {
    expect(parseFixedCostsCsv('Netflix,Entertainment,monthly,18.90')).toEqual([
      { name: 'Netflix', category: 'Entertainment', paymentInterval: 'MONTHLY', amount: 18.9 },
    ]);
  });

  it('prefers the semicolon delimiter when both are present', () => {
    expect(parseFixedCostsCsv('Miete, Wohnung;Wohnen;monatl.;1,850.00')).toEqual([
      { name: 'Miete, Wohnung', category: 'Wohnen', paymentInterval: 'MONTHLY', amount: 1850 },
    ]);
  });

  it.each([
    ['monatl.', 'MONTHLY'],
    ['monatlich', 'MONTHLY'],
    ['monthly', 'MONTHLY'],
    ['m', 'MONTHLY'],
    ['jährl.', 'YEARLY'],
    ['jaehrlich', 'YEARLY'],
    ['jährlich', 'YEARLY'],
    ['yearly', 'YEARLY'],
    ['j', 'YEARLY'],
    ['y', 'YEARLY'],
  ])('maps the billing variant "%s" to %s', (billing, expected) => {
    expect(parseFixedCostsCsv(`Netflix;;${billing};10`)).toEqual([
      { name: 'Netflix', category: undefined, paymentInterval: expected, amount: 10 },
    ]);
  });

  it('matches billing variants case-insensitively', () => {
    const text = ['Netflix;;MONTHLY;10', 'Steuern;;Jährl.;2000'].join('\n');

    expect(parseFixedCostsCsv(text)).toEqual([
      { name: 'Netflix', category: undefined, paymentInterval: 'MONTHLY', amount: 10 },
      { name: 'Steuern', category: undefined, paymentInterval: 'YEARLY', amount: 2000 },
    ]);
  });

  it("normalises Swiss amounts (' and ’ and space separators, comma decimals)", () => {
    const text = [
      "Krankenkasse;Versicherung;monatl.;2'460.00",
      'Miete;Wohnen;monatl.;1’850',
      'Steuern;Staat;jährl.;12 500.25',
      'Handy;Kommunikation;monatl.;39,95',
    ].join('\n');

    const amounts = parseFixedCostsCsv(text).map((item) => item.amount);
    expect(amounts).toEqual([2460, 1850, 12_500.25, 39.95]);
  });

  it('maps an empty category to undefined', () => {
    expect(parseFixedCostsCsv('Netflix;;monthly;18.90')).toEqual([
      { name: 'Netflix', category: undefined, paymentInterval: 'MONTHLY', amount: 18.9 },
    ]);
  });

  it('skips invalid rows and keeps the valid ones', () => {
    const text = [
      'Krankenkasse;Versicherung;monatl.;205',
      'Netflix;Entertainment;weekly;18.90',
      'Miete;Wohnen;monatl.;-100',
      'Handy;Kommunikation;monatl.;abc',
      ';Sport;jährl.;699',
      'Kurzzeile;monatl.',
      'ActiveFitness;Sport;jährl.;699',
    ].join('\n');

    expect(parseFixedCostsCsv(text)).toEqual([
      { name: 'Krankenkasse', category: 'Versicherung', paymentInterval: 'MONTHLY', amount: 205 },
      { name: 'ActiveFitness', category: 'Sport', paymentInterval: 'YEARLY', amount: 699 },
    ]);
  });

  it('skips blank lines', () => {
    expect(parseFixedCostsCsv('\nNetflix;;monthly;18.90\n\n')).toHaveLength(1);
  });

  it('returns nothing for an empty file', () => {
    expect(parseFixedCostsCsv('')).toEqual([]);
  });
});
