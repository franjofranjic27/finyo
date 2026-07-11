import { describe, expect, it } from 'vitest';
import { parsePositionsCsv } from './csv';

describe('parsePositionsCsv', () => {
  it('parses semicolon-delimited rows and skips the header', () => {
    const text = [
      'Name;ISIN;Anzahl;Kaufkurs;Kurs',
      'Nestlé SA;CH0038863350;10;90.50;100',
      'Roche Holding AG;CH0012032048;5;250;',
    ].join('\n');

    const { positions, errors } = parsePositionsCsv(text);

    expect(errors).toEqual([]);
    expect(positions).toEqual([
      { name: 'Nestlé SA', isin: 'CH0038863350', quantity: 10, purchasePrice: 90.5, currentPrice: 100 },
      { name: 'Roche Holding AG', isin: 'CH0012032048', quantity: 5, purchasePrice: 250, currentPrice: undefined },
    ]);
  });

  it('parses comma-delimited rows without a header', () => {
    const { positions, errors } = parsePositionsCsv('Nestlé SA,CH0038863350,10,90.50,100');

    expect(errors).toEqual([]);
    expect(positions).toEqual([
      { name: 'Nestlé SA', isin: 'CH0038863350', quantity: 10, purchasePrice: 90.5, currentPrice: 100 },
    ]);
  });

  it('prefers the semicolon delimiter when both are present', () => {
    const { positions, errors } = parsePositionsCsv('Fonds A,B;CH0000000000;2;1,234.56;');

    expect(errors).toEqual([]);
    expect(positions).toEqual([
      { name: 'Fonds A,B', isin: 'CH0000000000', quantity: 2, purchasePrice: 1234.56, currentPrice: undefined },
    ]);
  });

  it("normalises Swiss thousand separators (' and ’) and spaces", () => {
    const text = [
      "Nestlé SA;CH0038863350;1'000;12'500.50;",
      'Roche Holding AG;CH0012032048;2’000;1 250.25;',
    ].join('\n');

    const { positions, errors } = parsePositionsCsv(text);

    expect(errors).toEqual([]);
    expect(positions[0]).toMatchObject({ quantity: 1000, purchasePrice: 12_500.5 });
    expect(positions[1]).toMatchObject({ quantity: 2000, purchasePrice: 1250.25 });
  });

  it('treats a single comma without a dot as the decimal separator', () => {
    const { positions, errors } = parsePositionsCsv('Nestlé SA;CH0038863350;2,5;90,25;110,0');

    expect(errors).toEqual([]);
    expect(positions).toEqual([
      { name: 'Nestlé SA', isin: 'CH0038863350', quantity: 2.5, purchasePrice: 90.25, currentPrice: 110 },
    ]);
  });

  it('accepts rows with only a name or only an isin', () => {
    const text = ['Private Fund;;1;100;', ';CH0038863350;2;90;'].join('\n');

    const { positions, errors } = parsePositionsCsv(text);

    expect(errors).toEqual([]);
    expect(positions[0]).toMatchObject({ name: 'Private Fund', isin: undefined });
    expect(positions[1]).toMatchObject({ name: undefined, isin: 'CH0038863350' });
  });

  it('collects per-line errors with the original line numbers and keeps valid rows', () => {
    const text = [
      'Name;ISIN;Anzahl;Kaufkurs;Kurs',
      'Nestlé SA;CH0038863350;10;90;',
      ';;5;100;',
      'Roche Holding AG;CH0012032048;abc;250;',
      'Novartis AG;CH0012005267;3;-1;',
      'UBS Group AG;CH0244767585;1;25;xyz',
      'Zurich Insurance;CH0011075394',
    ].join('\n');

    const { positions, errors } = parsePositionsCsv(text);

    expect(positions).toHaveLength(1);
    expect(errors).toEqual([
      'Line 3: name or isin is required',
      'Line 4: invalid quantity "abc"',
      'Line 5: invalid purchase price "-1"',
      'Line 6: invalid price "xyz"',
      'Line 7: expected at least 4 columns (name, isin, quantity, purchase price)',
    ]);
  });

  it('rejects a zero quantity', () => {
    const { positions, errors } = parsePositionsCsv('Nestlé SA;CH0038863350;0;90;');

    expect(positions).toEqual([]);
    expect(errors).toEqual(['Line 1: invalid quantity "0"']);
  });

  it('skips blank lines without producing errors', () => {
    const text = '\nNestlé SA;CH0038863350;10;90;\n\n';

    const { positions, errors } = parsePositionsCsv(text);

    expect(errors).toEqual([]);
    expect(positions).toHaveLength(1);
  });

  it('returns nothing for an empty file', () => {
    expect(parsePositionsCsv('')).toEqual({ positions: [], errors: [] });
  });
});
