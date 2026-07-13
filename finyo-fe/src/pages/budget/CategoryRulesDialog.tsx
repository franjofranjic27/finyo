import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Pencil, X } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select';
import { useAuth } from '@/auth/useAuth';
import { ApiError } from '@/api/client';
import { categoriesApi } from '@/api/categories';
import { categoryRulesApi } from '@/api/categoryRules';
import type { CategoryRule, CategoryRuleInput } from '@/api/categoryRules';
import type { Category } from '@/types';

/**
 * The backend answers a duplicate keyword with 409 and an English detail message —
 * translate it, and fall back to the raw detail for everything else.
 */
function ruleErrorMessage(error: Error, t: (key: string) => string): string {
  return error instanceof ApiError && error.status === 409
    ? t('budget.rules.duplicate')
    : error.message;
}

interface RuleFormProps {
  idPrefix: string;
  categories: Category[];
  initial?: CategoryRuleInput;
  submitLabel: string;
  pending: boolean;
  onSubmit: (data: CategoryRuleInput) => void;
  onCancel?: () => void;
}

function RuleForm({
  idPrefix,
  categories,
  initial,
  submitLabel,
  pending,
  onSubmit,
  onCancel,
}: Readonly<RuleFormProps>) {
  const { t } = useTranslation();
  const [keyword, setKeyword] = useState(initial?.keyword ?? '');
  const [categoryId, setCategoryId] = useState(initial?.categoryId ?? '');
  const canSubmit = Boolean(keyword.trim()) && Boolean(categoryId);

  return (
    <div className="flex flex-wrap items-end gap-2">
      <div className="min-w-40 flex-1 space-y-1">
        <Label htmlFor={`${idPrefix}-keyword`}>{t('budget.rules.keyword')}</Label>
        <Input
          id={`${idPrefix}-keyword`}
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
        />
      </div>
      <div className="min-w-40 flex-1 space-y-1">
        <Label htmlFor={`${idPrefix}-category`}>{t('budget.rules.category')}</Label>
        <Select value={categoryId} onValueChange={setCategoryId}>
          <SelectTrigger id={`${idPrefix}-category`}>
            <SelectValue placeholder={t('budget.rules.categoryPlaceholder')} />
          </SelectTrigger>
          <SelectContent>
            {categories.map((category) => (
              <SelectItem key={category.id} value={category.id}>
                {category.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      <div className="flex gap-1">
        <Button
          size="sm"
          disabled={!canSubmit || pending}
          onClick={() => onSubmit({ keyword: keyword.trim(), categoryId })}
        >
          {pending ? t('common.loading') : submitLabel}
        </Button>
        {onCancel && (
          <Button size="sm" variant="outline" onClick={onCancel}>
            {t('common.cancel')}
          </Button>
        )}
      </div>
    </div>
  );
}

function RuleRow({
  rule,
  onEdit,
  onDelete,
}: Readonly<{ rule: CategoryRule; onEdit: () => void; onDelete: () => void }>) {
  const { t } = useTranslation();

  return (
    <li className="flex items-center gap-2 border-b border-border py-2 text-sm last:border-0">
      <span className="font-medium">{rule.keyword}</span>
      <span className="text-muted-foreground">→</span>
      <span className="inline-flex items-center gap-1.5 rounded-full bg-secondary px-2 py-0.5 text-xs font-medium">
        <span
          className="h-2 w-2 rounded-full bg-muted-foreground"
          style={rule.categoryColor ? { backgroundColor: rule.categoryColor } : undefined}
        />
        {rule.categoryName}
      </span>
      <span className="ml-auto inline-flex items-center gap-1">
        <Button
          variant="ghost"
          size="icon"
          className="h-7 w-7 text-muted-foreground"
          aria-label={t('budget.rules.edit')}
          onClick={onEdit}
        >
          <Pencil className="h-3.5 w-3.5" />
        </Button>
        <Button
          variant="ghost"
          size="icon"
          className="h-7 w-7 text-muted-foreground hover:text-red-500"
          aria-label={t('budget.rules.delete')}
          onClick={onDelete}
        >
          <X className="h-3.5 w-3.5" />
        </Button>
      </span>
    </li>
  );
}

/** Manage keyword → category rules used to auto-suggest import categories. */
export function CategoryRulesDialog({ onClose }: Readonly<{ onClose: () => void }>) {
  const { t } = useTranslation();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';
  const queryClient = useQueryClient();

  const [editing, setEditing] = useState<CategoryRule | null>(null);

  const rules = useQuery({
    queryKey: ['category-rules'],
    queryFn: () => categoryRulesApi.getAll(token),
    enabled: !!token,
  });

  const categories = useQuery({
    queryKey: ['categories'],
    queryFn: () => categoriesApi.getAll(token),
    enabled: !!token,
  });

  const invalidateRules = () =>
    queryClient.invalidateQueries({ queryKey: ['category-rules'] });

  const create = useMutation({
    mutationFn: (data: CategoryRuleInput) => categoryRulesApi.create(token, data),
    onSuccess: invalidateRules,
  });

  const update = useMutation({
    mutationFn: ({ id, data }: { id: string; data: CategoryRuleInput }) =>
      categoryRulesApi.update(token, id, data),
    onSuccess: () => {
      setEditing(null);
      invalidateRules();
    },
  });

  const remove = useMutation({
    mutationFn: (id: string) => categoryRulesApi.delete(token, id),
    onSuccess: invalidateRules,
  });

  const confirmDelete = (rule: CategoryRule) => {
    if (globalThis.confirm(t('budget.rules.confirmDelete', { keyword: rule.keyword }))) {
      remove.mutate(rule.id);
    }
  };

  const mutationError = create.error ?? update.error ?? remove.error;

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{t('budget.rules.title')}</DialogTitle>
          <DialogDescription>{t('budget.rules.hint')}</DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          {rules.data && rules.data.length === 0 && (
            <p className="py-4 text-center text-sm text-muted-foreground">
              {t('budget.rules.empty')}
            </p>
          )}
          {rules.data && rules.data.length > 0 && (
            <ul>
              {rules.data.map((rule) =>
                editing?.id === rule.id ? (
                  <li key={rule.id} className="border-b border-border py-2 last:border-0">
                    <RuleForm
                      idPrefix={`rule-${rule.id}`}
                      categories={categories.data ?? []}
                      initial={{ keyword: rule.keyword, categoryId: rule.categoryId }}
                      submitLabel={t('common.save')}
                      pending={update.isPending}
                      onSubmit={(data) => update.mutate({ id: rule.id, data })}
                      onCancel={() => setEditing(null)}
                    />
                  </li>
                ) : (
                  <RuleRow
                    key={rule.id}
                    rule={rule}
                    onEdit={() => setEditing(rule)}
                    onDelete={() => confirmDelete(rule)}
                  />
                ),
              )}
            </ul>
          )}

          {!editing && (
            <RuleForm
              // Remount after a successful create so the form clears.
              key={rules.data?.length ?? 0}
              idPrefix="rule-new"
              categories={categories.data ?? []}
              submitLabel={t('budget.rules.add')}
              pending={create.isPending}
              onSubmit={(data) => create.mutate(data)}
            />
          )}

          {mutationError && (
            <p className="text-sm text-destructive">{ruleErrorMessage(mutationError, t)}</p>
          )}
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            {t('common.close')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

interface CreateRuleDialogProps {
  /** Prefilled from the transaction description. */
  keyword: string;
  /** Preselected category from the row's select. */
  categoryId: string;
  onClose: () => void;
}

/** Thin "remember as rule" wrapper around the shared rule form. */
export function CreateRuleDialog({ keyword, categoryId, onClose }: Readonly<CreateRuleDialogProps>) {
  const { t } = useTranslation();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';
  const queryClient = useQueryClient();

  const categories = useQuery({
    queryKey: ['categories'],
    queryFn: () => categoriesApi.getAll(token),
    enabled: !!token,
  });

  const create = useMutation({
    mutationFn: (data: CategoryRuleInput) => categoryRulesApi.create(token, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['category-rules'] });
      onClose();
    },
  });

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>{t('budget.rules.createTitle')}</DialogTitle>
          <DialogDescription>{t('budget.rules.hint')}</DialogDescription>
        </DialogHeader>
        <RuleForm
          idPrefix="rule-remember"
          categories={categories.data ?? []}
          initial={{ keyword, categoryId }}
          submitLabel={t('budget.rules.add')}
          pending={create.isPending}
          onSubmit={(data) => create.mutate(data)}
          onCancel={onClose}
        />
        {create.error && (
          <p className="text-sm text-destructive">{ruleErrorMessage(create.error, t)}</p>
        )}
      </DialogContent>
    </Dialog>
  );
}
