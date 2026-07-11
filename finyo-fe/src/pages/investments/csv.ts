import type { CreatePositionRequest } from '@/api/portfolio';
import { csvRows, parseSwissNumber } from '@/lib/csv';

export interface CsvParseResult {
  positions: CreatePositionRequest[];
  errors: string[];
}

/** A first row whose numeric columns (quantity/prices) do not parse is a header. */
function isHeaderRow(cells: string[]): boolean {
  const numericCells = cells.slice(2, 5).filter((cell) => cell !== '');
  return numericCells.length > 0 && numericCells.some((cell) => Number.isNaN(parseSwissNumber(cell)));
}

function parseRow(cells: string[], lineNumber: number): CreatePositionRequest | string {
  if (cells.length < 4) {
    return `Line ${lineNumber}: expected at least 4 columns (name, isin, quantity, purchase price)`;
  }

  const [name, isin, quantityRaw, purchasePriceRaw, currentPriceRaw] = cells;
  if (!name && !isin) {
    return `Line ${lineNumber}: name or isin is required`;
  }

  const quantity = parseSwissNumber(quantityRaw);
  if (Number.isNaN(quantity) || quantity <= 0) {
    return `Line ${lineNumber}: invalid quantity "${quantityRaw}"`;
  }

  const purchasePrice = parseSwissNumber(purchasePriceRaw);
  if (Number.isNaN(purchasePrice) || purchasePrice < 0) {
    return `Line ${lineNumber}: invalid purchase price "${purchasePriceRaw}"`;
  }

  let currentPrice: number | undefined;
  if (currentPriceRaw) {
    currentPrice = parseSwissNumber(currentPriceRaw);
    if (Number.isNaN(currentPrice) || currentPrice < 0) {
      return `Line ${lineNumber}: invalid price "${currentPriceRaw}"`;
    }
  }

  return {
    name: name || undefined,
    isin: isin || undefined,
    quantity,
    purchasePrice,
    currentPrice,
  };
}

/**
 * Parses a positions CSV with the columns
 * `Name; ISIN (optional); Quantity; Purchase price; Price (optional)`.
 * Collects per-line errors instead of aborting — mirroring the backend's
 * bulk import contract.
 */
export function parsePositionsCsv(text: string): CsvParseResult {
  const positions: CreatePositionRequest[] = [];
  const errors: string[] = [];

  for (const { cells, lineNumber } of csvRows(text, isHeaderRow)) {
    const result = parseRow(cells, lineNumber);
    if (typeof result === 'string') {
      errors.push(result);
    } else {
      positions.push(result);
    }
  }

  return { positions, errors };
}
