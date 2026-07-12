/** Shared CSV primitives for the client-side bulk importers (positions, fixed costs). */

export interface CsvRow {
  cells: string[];
  lineNumber: number;
}

/**
 * Normalises Swiss-formatted numbers: strips apostrophe (' and ’) and space
 * thousand separators; a single comma without a dot is treated as the
 * decimal separator, otherwise commas are thousand separators.
 */
export function parseSwissNumber(raw: string): number {
  let cleaned = raw.replaceAll(/['’\s]/g, '');
  if (cleaned.includes(',')) {
    const hasSingleComma = cleaned.indexOf(',') === cleaned.lastIndexOf(',');
    cleaned = hasSingleComma && !cleaned.includes('.')
      ? cleaned.replace(',', '.')
      : cleaned.replaceAll(',', '');
  }
  if (!/^-?(\d+\.?\d*|\.\d+)$/.test(cleaned)) return Number.NaN;
  return Number.parseFloat(cleaned);
}

/**
 * Splits CSV text into trimmed cell rows: detects the delimiter (`;` wins
 * over `,`), skips blank lines and drops the first row when the caller's
 * heuristic identifies it as a header. Line numbers are 1-based positions
 * in the original file, so per-line error messages stay accurate.
 */
export function csvRows(text: string, isHeaderRow: (cells: string[]) => boolean): CsvRow[] {
  const delimiter = text.includes(';') ? ';' : ',';
  const rows: CsvRow[] = [];
  let firstRowSeen = false;

  text.split(/\r?\n/).forEach((line, index) => {
    if (!line.trim()) return;

    const cells = line.split(delimiter).map((cell) => cell.trim());
    if (!firstRowSeen) {
      firstRowSeen = true;
      if (isHeaderRow(cells)) return;
    }

    rows.push({ cells, lineNumber: index + 1 });
  });

  return rows;
}
