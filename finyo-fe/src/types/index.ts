// Account types
export type AccountType =
  | 'CHECKING'
  | 'SAVINGS'
  | 'CREDIT_CARD'
  | 'INVESTMENT'
  | 'VORSORGE'
  | 'CASH'
  | 'OTHER';

/** Grouping of accounts and cards (mirrors the backend enum). */
export type AccountScope = 'PRIVATE' | 'BUSINESS';

export interface Account {
  id: string;
  name: string;
  type: AccountType;
  currency: string;
  initialBalance: string;
  color?: string;
  /** Normalized (no spaces, uppercase) — format for display with formatIban */
  iban: string | null;
  bic: string | null;
  contractNumber: string | null;
  feeNote: string | null;
  scope: AccountScope;
  toClose: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateAccountRequest {
  name: string;
  type: AccountType;
  currency: string;
  /** Optional — the backend defaults to zero on create and keeps the stored value on update. */
  initialBalance?: string;
  color?: string;
  iban?: string;
  bic?: string;
  contractNumber?: string;
  feeNote?: string;
  scope?: AccountScope;
  toClose?: boolean;
}

/** Result of the bulk import — rows are matched by normalized IBAN, else by normalized name (upsert). */
export interface AccountBulkResult {
  created: number;
  updated: number;
  failed: number;
  errors: string[];
}

export interface PaymentCard {
  id: string;
  name: string;
  provider: string | null;
  accountId: string | null;
  accountName: string | null;
  currency: string | null;
  feeNote: string | null;
  scope: AccountScope;
}

export interface PaymentCardInput {
  name: string;
  provider?: string;
  accountId?: string;
  currency?: string;
  feeNote?: string;
  scope?: AccountScope;
}

// Category types
export type CategoryType = 'EXPENSE' | 'INCOME';

export interface Category {
  id: string;
  name: string;
  parentId?: string;
  parentName?: string;
  icon?: string;
  color?: string;
  type: CategoryType;
}

// Analytics types
export type TimeRange =
  | 'LAST_7_DAYS'
  | 'LAST_30_DAYS'
  | 'THIS_MONTH'
  | 'LAST_MONTH'
  | 'LAST_3_MONTHS'
  | 'LAST_6_MONTHS'
  | 'LAST_12_MONTHS';

export interface SpendingSummary {
  totalExpenses: string;
  totalIncome: string;
  netAmount: string;
  rangeStart: string;
  rangeEnd: string;
  transactionCount: number;
}

export interface CategoryBreakdown {
  categoryId: string;
  categoryName: string;
  categoryColor?: string;
  total: string;
  percentage: number;
}

export interface MonthlyDataPoint {
  year: number;
  month: number;
  expenses: string;
  income: string;
  net: string;
}
