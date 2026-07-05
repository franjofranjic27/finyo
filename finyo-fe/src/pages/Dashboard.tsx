import type React from 'react';
import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import {
  PieChart,
  Pie,
  Cell,
  Tooltip,
  ResponsiveContainer,
  Legend,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
} from 'recharts';
import { TrendingUp, TrendingDown, Wallet, Tag } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { useAuth } from '@/auth/useAuth';
import { analyticsApi } from '@/api/analytics';
import { formatCHF, amountColour } from '@/lib/formatters';
import type { CategoryBreakdown, MonthlyDataPoint } from '@/types';

const CHART_COLOURS = ['#6366f1', '#8b5cf6', '#a78bfa', '#c4b5fd', '#ddd6fe', '#e0e7ff'];

// --- Sub-components ---

function SummaryCard({
  title,
  value,
  sub,
  icon: Icon,
  valueClass,
}: {
  title: string;
  value: string;
  sub?: string;
  icon: React.ComponentType<{ className?: string }>;
  valueClass?: string;
}) {
  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between pb-2">
        <CardTitle className="text-sm font-medium text-muted-foreground">{title}</CardTitle>
        <Icon className="h-4 w-4 text-muted-foreground" />
      </CardHeader>
      <CardContent>
        <p className={`text-2xl font-bold ${valueClass ?? 'text-foreground'}`}>{value}</p>
        {sub && <p className="mt-1 text-xs text-muted-foreground">{sub}</p>}
      </CardContent>
    </Card>
  );
}

function SummaryCardSkeleton() {
  return (
    <Card>
      <CardHeader className="pb-2">
        <Skeleton className="h-4 w-28" />
      </CardHeader>
      <CardContent>
        <Skeleton className="h-8 w-36" />
        <Skeleton className="mt-2 h-3 w-24" />
      </CardContent>
    </Card>
  );
}

function SpendingDonut({ data }: { data: CategoryBreakdown[] }) {
  const chartData = data.slice(0, 6).map((d) => ({
    name: d.categoryName,
    value: Math.abs(parseFloat(d.total)),
  }));

  return (
    <ResponsiveContainer width="100%" height={260}>
      <PieChart>
        <Pie
          data={chartData}
          cx="50%"
          cy="50%"
          innerRadius={60}
          outerRadius={100}
          paddingAngle={3}
          dataKey="value"
        >
          {chartData.map((_, index) => (
            <Cell key={index} fill={CHART_COLOURS[index % CHART_COLOURS.length]} />
          ))}
        </Pie>
        <Tooltip
          formatter={(value: number) => formatCHF(value)}
          contentStyle={{ background: 'hsl(var(--card))', border: '1px solid hsl(var(--border))' }}
        />
        <Legend
          formatter={(value) => (
            <span style={{ color: 'hsl(var(--foreground))', fontSize: 12 }}>{value}</span>
          )}
        />
      </PieChart>
    </ResponsiveContainer>
  );
}

function MonthlyBarChart({ data }: { data: MonthlyDataPoint[] }) {
  const chartData = data.slice(-6).map((d) => ({
    month: `${String(d.month).padStart(2, '0')}/${d.year}`,
    Income: Math.abs(parseFloat(d.income)),
    Expenses: Math.abs(parseFloat(d.expenses)),
  }));

  return (
    <ResponsiveContainer width="100%" height={260}>
      <BarChart data={chartData} barGap={4}>
        <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" />
        <XAxis
          dataKey="month"
          tick={{ fill: 'hsl(var(--muted-foreground))', fontSize: 11 }}
          axisLine={false}
          tickLine={false}
        />
        <YAxis
          tick={{ fill: 'hsl(var(--muted-foreground))', fontSize: 11 }}
          axisLine={false}
          tickLine={false}
          tickFormatter={(v: number) => `${(v / 1000).toFixed(0)}k`}
        />
        <Tooltip
          formatter={(value: number) => formatCHF(value)}
          contentStyle={{ background: 'hsl(var(--card))', border: '1px solid hsl(var(--border))' }}
        />
        <Bar dataKey="Income" fill="#10b981" radius={[3, 3, 0, 0]} />
        <Bar dataKey="Expenses" fill="#6366f1" radius={[3, 3, 0, 0]} />
      </BarChart>
    </ResponsiveContainer>
  );
}

// --- Demo / placeholder data used when backend is offline ---

const DEMO_SUMMARY = {
  totalExpenses: '-3240.50',
  totalIncome: '7500.00',
  netAmount: '4259.50',
  rangeStart: '2026-02-01',
  rangeEnd: '2026-02-25',
  transactionCount: 34,
};

const DEMO_CATEGORIES: CategoryBreakdown[] = [
  { categoryId: '1', categoryName: 'Groceries', total: '-820.00', percentage: 25 },
  { categoryId: '2', categoryName: 'Transport', total: '-380.00', percentage: 12 },
  { categoryId: '3', categoryName: 'Dining', total: '-560.00', percentage: 17 },
  { categoryId: '4', categoryName: 'Entertainment', total: '-310.00', percentage: 10 },
  { categoryId: '5', categoryName: 'Health', total: '-420.00', percentage: 13 },
  { categoryId: '6', categoryName: 'Utilities', total: '-750.50', percentage: 23 },
];

const DEMO_MONTHLY: MonthlyDataPoint[] = [
  { year: 2025, month: 9, expenses: '-3100', income: '7200', net: '4100' },
  { year: 2025, month: 10, expenses: '-2900', income: '7500', net: '4600' },
  { year: 2025, month: 11, expenses: '-3600', income: '7500', net: '3900' },
  { year: 2025, month: 12, expenses: '-4100', income: '8200', net: '4100' },
  { year: 2026, month: 1, expenses: '-3050', income: '7500', net: '4450' },
  { year: 2026, month: 2, expenses: '-3240', income: '7500', net: '4260' },
];

// --- Main Dashboard component ---

export function Dashboard() {
  const { t } = useTranslation();
  const { accessToken } = useAuth();

  const token = accessToken ?? '';

  const { data: summary, isLoading: loadingSummary } = useQuery({
    queryKey: ['analytics', 'summary', 'THIS_MONTH'],
    queryFn: () => analyticsApi.getSummary(token, 'THIS_MONTH'),
    enabled: !!token,
    placeholderData: DEMO_SUMMARY,
  });

  const { data: categories, isLoading: loadingCategories } = useQuery({
    queryKey: ['analytics', 'categories', 'THIS_MONTH'],
    queryFn: () => analyticsApi.getCategoryBreakdown(token, 'THIS_MONTH'),
    enabled: !!token,
    placeholderData: DEMO_CATEGORIES,
  });

  const { data: monthly, isLoading: loadingMonthly } = useQuery({
    queryKey: ['analytics', 'monthly'],
    queryFn: () => analyticsApi.getMonthlyTrend(token, 'LAST_6_MONTHS'),
    enabled: !!token,
    placeholderData: DEMO_MONTHLY,
  });

  const summaryData = summary ?? DEMO_SUMMARY;
  const categoriesData = categories ?? DEMO_CATEGORIES;
  const monthlyData = monthly ?? DEMO_MONTHLY;

  const biggestCategory = categoriesData.reduce<CategoryBreakdown | null>((acc, c) => {
    if (!acc || Math.abs(parseFloat(c.total)) > Math.abs(parseFloat(acc.total))) return c;
    return acc;
  }, null);

  return (
    <div className="space-y-6">
      {/* Summary cards */}
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        {loadingSummary ? (
          Array.from({ length: 4 }).map((_, i) => <SummaryCardSkeleton key={i} />)
        ) : (
          <>
            <SummaryCard
              title={t('dashboard.netThisMonth')}
              value={formatCHF(summaryData.netAmount)}
              icon={Wallet}
              valueClass={amountColour(parseFloat(summaryData.netAmount))}
              sub={`${summaryData.transactionCount} transactions`}
            />
            <SummaryCard
              title={t('dashboard.totalIncome')}
              value={formatCHF(summaryData.totalIncome)}
              icon={TrendingUp}
              valueClass="text-emerald-500"
            />
            <SummaryCard
              title={t('dashboard.totalSpent')}
              value={formatCHF(summaryData.totalExpenses)}
              icon={TrendingDown}
              valueClass="text-red-500"
            />
            <SummaryCard
              title={t('dashboard.biggestCategory')}
              value={biggestCategory?.categoryName ?? '—'}
              icon={Tag}
              sub={biggestCategory ? formatCHF(biggestCategory.total) : undefined}
            />
          </>
        )}
      </div>

      {/* Charts row */}
      <div className="grid gap-4 lg:grid-cols-2">
        {/* Spending donut */}
        <Card>
          <CardHeader>
            <CardTitle className="text-base">{t('dashboard.spendingByCategory')}</CardTitle>
          </CardHeader>
          <CardContent>
            {loadingCategories ? (
              <Skeleton className="h-64 w-full" />
            ) : (
              <SpendingDonut data={categoriesData} />
            )}
          </CardContent>
        </Card>

        {/* Monthly bar chart */}
        <Card>
          <CardHeader>
            <CardTitle className="text-base">{t('dashboard.monthlyOverview')}</CardTitle>
          </CardHeader>
          <CardContent>
            {loadingMonthly ? (
              <Skeleton className="h-64 w-full" />
            ) : (
              <MonthlyBarChart data={monthlyData} />
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
