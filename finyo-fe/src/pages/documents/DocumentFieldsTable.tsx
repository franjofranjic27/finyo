import { useTranslation } from 'react-i18next';
import { Checkbox } from '@/components/ui/checkbox';
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/components/ui/table';
import type { ExtractedField } from '@/api/documents';
import { formatCHF } from '@/lib/formatters';

interface DocumentFieldsTableProps {
  fields: readonly ExtractedField[];
  /** Selection state keyed by fieldName; only mapped fields appear here. */
  selected: Readonly<Record<string, boolean>>;
  onToggle: (fieldName: string, checked: boolean) => void;
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
                  <span className="inline-flex items-center rounded-full bg-muted px-2 py-0.5 text-xs font-medium">
                    {t('documents.table.infoOnly')}
                  </span>
                )}
              </TableCell>
            </TableRow>
          );
        })}
      </TableBody>
    </Table>
  );
}
