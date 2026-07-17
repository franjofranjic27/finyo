import { useTranslation } from 'react-i18next';
import { Checkbox } from '@/components/ui/checkbox';
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/components/ui/table';
import type { ExtractedField } from '@/api/documents';
import { formatCHF } from '@/lib/formatters';
import { cn } from '@/lib/utils';

interface DocumentFieldsTableProps {
  fields: readonly ExtractedField[];
  /** Selection state keyed by fieldName; only mapped fields appear here. */
  selected: Readonly<Record<string, boolean>>;
  onToggle: (fieldName: string, checked: boolean) => void;
}

function InfoOnlyChip() {
  const { t } = useTranslation();
  return (
    <span className="inline-flex items-center rounded-full bg-muted px-2 py-0.5 text-xs font-medium">
      {t('documents.table.infoOnly')}
    </span>
  );
}

/** Mobile row: label with target below, value on the right — the table does not fit on 390 px. */
function FieldListItem({ field, selected, onToggle }: Readonly<{
  field: ExtractedField;
  selected: Readonly<Record<string, boolean>>;
  onToggle: (fieldName: string, checked: boolean) => void;
}>) {
  const { t } = useTranslation();
  const target = field.targetTaxYearField;

  return (
    <li
      className={cn(
        'flex items-center gap-3 border-b border-border py-2.5 last:border-0',
        !target && 'text-muted-foreground',
      )}
    >
      <span className="w-4 shrink-0">
        {target && (
          <Checkbox
            aria-label={field.label}
            checked={selected[field.fieldName] ?? false}
            onCheckedChange={(value) => onToggle(field.fieldName, value === true)}
          />
        )}
      </span>
      <div className="min-w-0 flex-1">
        <p className={cn('text-sm', target && 'font-medium')}>{field.label}</p>
        {target && (
          <p className="text-xs text-muted-foreground">
            <span aria-hidden="true">→ </span>
            {t(`tax.upload.targets.${target}`, { defaultValue: target })}
          </p>
        )}
      </div>
      <div className="shrink-0 text-right">
        <p className="text-sm font-medium tabular-nums">{formatCHF(field.value)}</p>
        {!target && <InfoOnlyChip />}
      </div>
    </li>
  );
}

/**
 * Extracted values of a synced document: mapped fields are selectable, values
 * without a tax-year target are shown for context only.
 */
export function DocumentFieldsTable({
  fields,
  selected,
  onToggle,
}: Readonly<DocumentFieldsTableProps>) {
  const { t } = useTranslation();

  return (
    <>
      <ul className="md:hidden">
        {fields.map((field) => (
          <FieldListItem
            key={field.fieldName}
            field={field}
            selected={selected}
            onToggle={onToggle}
          />
        ))}
      </ul>

      <div className="hidden md:block">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="w-8" />
              <TableHead>{t('documents.table.field')}</TableHead>
              <TableHead className="text-right">{t('documents.table.value')}</TableHead>
              <TableHead>{t('documents.table.target')}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {fields.map((field) => {
              const target = field.targetTaxYearField;
              return (
                <TableRow key={field.fieldName} className={target ? '' : 'text-muted-foreground'}>
                  <TableCell>
                    {target && (
                      <Checkbox
                        aria-label={field.label}
                        checked={selected[field.fieldName] ?? false}
                        onCheckedChange={(value) => onToggle(field.fieldName, value === true)}
                      />
                    )}
                  </TableCell>
                  <TableCell>{field.label}</TableCell>
                  <TableCell className="text-right font-medium">{formatCHF(field.value)}</TableCell>
                  <TableCell className="text-muted-foreground">
                    {target ? (
                      // Target labels are the shared tax-year vocabulary of the upload dialog.
                      t(`tax.upload.targets.${target}`, { defaultValue: target })
                    ) : (
                      <InfoOnlyChip />
                    )}
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </div>
    </>
  );
}
