import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Check, ChevronLeft, ChevronRight, Upload } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select';
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/components/ui/table';
import { useAuth } from '@/auth/useAuth';
import { categoriesApi } from '@/api/categories';
import { transactionsApi } from '@/api/transactions';
import type { Transaction } from '@/api/transactions';
import type { Category } from '@/types';
import { amountColour, formatCHF, formatDate } from '@/lib/formatters';
import { CreateRuleDialog } from './CategoryRulesDialog';

const PAGE_SIZE = 50;

/** Sentinel for "no category" — Radix Select does not allow empty item values. */
const NONE = 'none';

interface RuleHint {
  transactionId: string;
  keyword: string;
  categoryId: string;
}

interface CategoryCellProps {
  transaction: Transaction;
  categories: Category[];
  hint: RuleHint | null;
  pending: boolean;
  onChange: (categoryId: string | null) => void;
  onRememberRule: () => void;
}

function CategoryCell({
  transaction,
  categories,
  hint,
  pending,
  onChange,
  onRememberRule,
}: Readonly<CategoryCellProps>) {
  const { t } = useTranslation();
  const showHint = hint?.transactionId === transaction.id;

  return (
    <div className="flex items-center gap-2">
      <Select
        value={transaction.categoryId ?? NONE}
        disabled={pending}
        onValueChange={(value) => {
          const categoryId = value === NONE ? null : value;
          if (categoryId !== transaction.categoryId) onChange(categoryId);
        }}
      >
        <SelectTrigger
          className="h-8 w-44"
          aria-label={`${t('budget.rules.category')}: ${transaction.description ?? ''}`}
        >
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value={NONE}>—</SelectItem>
          {categories.map((category) => (
            <SelectItem key={category.id} value={category.id}>
              {category.name}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
      {showHint && (
        <span className="inline-flex items-center gap-1.5 whitespace-nowrap">
          <Check aria-label={t('budget.month.saved')} className="h-4 w-4 text-emerald-500" />
          <Button variant="outline" size="sm" className="h-7 px-2 text-xs" onClick={onRememberRule}>
            {t('budget.month.rememberRule')}
          </Button>
        </span>
      )}
    </div>
  );
}

interface MonthTransactionsTableProps {
  /** ISO first day of the month. */
  from: string;
  /** ISO last day of the month. */
  to: string;
  onImportClick: () => void;
}

/** Paged transaction list for one month with inline category assignment. */
export function MonthTransactionsTable({
  from,
  to,
  onImportClick,
}: Readonly<MonthTransactionsTableProps>) {
  const { t, i18n } = useTranslation();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';
  const queryClient = useQueryClient();

  const [page, setPage] = useState(0);
  const [ruleHint, setRuleHint] = useState<RuleHint | null>(null);
  const [ruleDialog, setRuleDialog] = useState<RuleHint | null>(null);

  const transactions = useQuery({
    queryKey: ['transactions', from, to, page],
    queryFn: () => transactionsApi.getAll(token, { from, to, page, size: PAGE_SIZE }),
    enabled: !!token,
  });

  const categories = useQuery({
    queryKey: ['categories'],
    queryFn: () => categoriesApi.getAll(token),
    enabled: !!token,
  });

  const updateCategory = useMutation({
    mutationFn: ({
      transaction,
      categoryId,
    }: {
      transaction: Transaction;
      categoryId: string | null;
    }) =>
      transactionsApi.update(token, transaction.id, {
        amount: transaction.amount,
        currency: transaction.currency,
        date: transaction.date,
        description: transaction.description,
        categoryId,
        accountId: transaction.accountId,
      }),
    onSuccess: (_, { transaction, categoryId }) => {
      queryClient.invalidateQueries({ queryKey: ['transactions'] });
      queryClient.invalidateQueries({ queryKey: ['analytics'] });
      setRuleHint(
        categoryId
          ? { transactionId: transaction.id, keyword: transaction.description ?? '', categoryId }
          : null,
      );
    },
  });

  const data = transactions.data;

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">{t('budget.month.transactions')}</CardTitle>
      </CardHeader>
      <CardContent>
        {transactions.isLoading && <Skeleton className="h-40 w-full" />}
        {transactions.isError && (
          <p className="text-sm text-destructive">{t('budget.month.loadError')}</p>
        )}

        {data && data.totalElements === 0 && (
          <div className="flex flex-col items-center gap-3 py-8">
            <p className="text-sm text-muted-foreground">{t('budget.month.noTransactions')}</p>
            <Button size="sm" onClick={onImportClick}>
              <Upload className="mr-1 h-4 w-4" />
              {t('budget.month.importCta')}
            </Button>
          </div>
        )}

        {data && data.totalElements > 0 && (
          <>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('budget.import.colDate')}</TableHead>
                  <TableHead>{t('budget.import.colDescription')}</TableHead>
                  <TableHead className="text-right">{t('budget.import.colAmount')}</TableHead>
                  <TableHead>{t('budget.rules.category')}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {data.content.map((transaction) => (
                  <TableRow key={transaction.id}>
                    <TableCell className="whitespace-nowrap">
                      {formatDate(transaction.date, i18n.language)}
                    </TableCell>
                    <TableCell className="max-w-72 truncate">
                      {transaction.description}
                    </TableCell>
                    <TableCell
                      className={`text-right tabular-nums ${amountColour(transaction.amount)}`}
                    >
                      {formatCHF(transaction.amount)}
                    </TableCell>
                    <TableCell>
                      <CategoryCell
                        transaction={transaction}
                        categories={categories.data ?? []}
                        hint={ruleHint}
                        pending={updateCategory.isPending}
                        onChange={(categoryId) =>
                          updateCategory.mutate({ transaction, categoryId })
                        }
                        onRememberRule={() => setRuleDialog(ruleHint)}
                      />
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>

            {updateCategory.isError && (
              <p className="mt-2 text-sm text-destructive">{updateCategory.error.message}</p>
            )}

            {data.totalPages > 1 && (
              <div className="mt-3 flex items-center justify-end gap-2 text-sm text-muted-foreground">
                <span>
                  {t('budget.month.pageOf', { page: data.number + 1, total: data.totalPages })}
                </span>
                <Button
                  variant="ghost"
                  size="icon"
                  aria-label={t('budget.month.prevPage')}
                  disabled={data.number === 0}
                  onClick={() => setPage((current) => current - 1)}
                >
                  <ChevronLeft className="h-4 w-4" />
                </Button>
                <Button
                  variant="ghost"
                  size="icon"
                  aria-label={t('budget.month.nextPage')}
                  disabled={data.number >= data.totalPages - 1}
                  onClick={() => setPage((current) => current + 1)}
                >
                  <ChevronRight className="h-4 w-4" />
                </Button>
              </div>
            )}
          </>
        )}
      </CardContent>

      {ruleDialog && (
        <CreateRuleDialog
          keyword={ruleDialog.keyword}
          categoryId={ruleDialog.categoryId}
          onClose={() => {
            setRuleDialog(null);
            setRuleHint(null);
          }}
        />
      )}
    </Card>
  );
}
