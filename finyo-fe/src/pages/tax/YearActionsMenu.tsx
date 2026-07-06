import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { MoreVertical, Trash2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { useAuth } from '@/auth/useAuth';
import { taxApi } from '@/api/tax';
import type { TaxYearStatus } from '@/api/tax';
import { allowedStatusTransitions, STATUS_LABEL_KEY } from './taxYearUtils';

interface YearActionsMenuProps {
  year: number;
  status: TaxYearStatus;
}

/** Status transitions and deletion for a persisted tax year. */
export function YearActionsMenu({ year, status }: Readonly<YearActionsMenuProps>) {
  const { t } = useTranslation();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  const invalidateYear = () => {
    queryClient.invalidateQueries({ queryKey: ['tax', 'year', String(year)] });
    queryClient.invalidateQueries({ queryKey: ['tax', 'years'] });
  };

  const updateStatus = useMutation({
    mutationFn: (target: TaxYearStatus) => taxApi.updateYearStatus(token, year, target),
    onSuccess: invalidateYear,
  });

  const deleteYear = useMutation({
    mutationFn: () => taxApi.deleteYear(token, year),
    onSuccess: () => {
      queryClient.removeQueries({ queryKey: ['tax', 'year', String(year)] });
      queryClient.invalidateQueries({ queryKey: ['tax', 'years'] });
      navigate(`/tax/${new Date().getFullYear()}`);
    },
  });

  const confirmDeleteYear = () => {
    if (globalThis.confirm(t('tax.confirmDeleteYear', { year }))) {
      deleteYear.mutate();
    }
  };

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button
          variant="ghost"
          size="icon"
          className="h-8 w-8"
          aria-label={t('tax.changeStatus')}
          disabled={updateStatus.isPending || deleteYear.isPending}
        >
          <MoreVertical className="h-4 w-4" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start">
        <DropdownMenuLabel>{t('tax.changeStatus')}</DropdownMenuLabel>
        {allowedStatusTransitions(status).map((target) => (
          <DropdownMenuItem key={target} onSelect={() => updateStatus.mutate(target)}>
            {t(STATUS_LABEL_KEY[target])}
          </DropdownMenuItem>
        ))}
        <DropdownMenuSeparator />
        <DropdownMenuItem
          className="text-destructive focus:text-destructive"
          onSelect={confirmDeleteYear}
        >
          <Trash2 className="h-4 w-4" />
          {t('tax.deleteYear')}
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
