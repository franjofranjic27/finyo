// Account types
export type AccountType = 'CHECKING' | 'SAVINGS' | 'CREDIT_CARD' | 'INVESTMENT' | 'CASH' | 'OTHER';

export interface Account {
  id: string;
  name: string;
  type: AccountType;
  currency: string;
  initialBalance: string;
  color?: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateAccountRequest {
  name: string;
  type: AccountType;
  currency: string;
  initialBalance: string;
  color?: string;
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
