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

// Transaction types
export type TransactionSource = 'MANUAL' | 'CSV_IMPORT';

export interface Transaction {
  id: string;
  amount: string;
  currency: string;
  date: string;
  description?: string;
  categoryId?: string;
  categoryName?: string;
  accountId: string;
  accountName: string;
  source: TransactionSource;
  createdAt: string;
}

export interface TransactionPage {
  content: Transaction[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
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

// Budget types
export interface Budget {
  id: string;
  categoryId: string;
  categoryName: string;
  categoryColor?: string;
  amount: string;
  period: 'MONTHLY' | 'WEEKLY';
  validFrom: string;
  validUntil?: string;
}

export interface BudgetStatus extends Budget {
  spent: string;
  remaining: string;
  percentageUsed: number;
}

// Savings goal types
export interface SavingsGoal {
  id: string;
  name: string;
  targetAmount: string;
  currentAmount: string;
  targetDate?: string;
  accountId?: string;
  icon?: string;
  color?: string;
  archived: boolean;
  progressPercentage: number;
  monthlyRequired?: string;
}

// Investment types
export interface Instrument {
  id: string;
  valor?: string;
  isin?: string;
  ticker?: string;
  name?: string;
  instrumentType?: string;
  sortOrder: number;
}

export interface MarketData {
  valor?: string;
  isin?: string;
  name: string;
  exchange: string;
  currency: string;
  lastPrice: number;
  change1d?: number;
  change1dPct?: number;
  change1w?: number;
  change1wPct?: number;
  change1m?: number;
  change1mPct?: number;
  changeYtd?: number;
  changeYtdPct?: number;
  change1y?: number;
  change1yPct?: number;
  high52w?: number;
  low52w?: number;
  marketCap?: number;
  dividendYield?: number;
  instrumentType?: string;
  sector?: string;
  ter?: number;
  dataAsOf?: string;
}
