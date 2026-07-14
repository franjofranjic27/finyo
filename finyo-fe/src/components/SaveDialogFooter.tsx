import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/button';
import { DialogFooter } from '@/components/ui/dialog';

interface SaveDialogFooterProps {
  onClose: () => void;
  onSave: () => void;
  canSubmit: boolean;
  pending: boolean;
}

/** Standard cancel/save footer for edit dialogs. */
export function SaveDialogFooter({
  onClose,
  onSave,
  canSubmit,
  pending,
}: Readonly<SaveDialogFooterProps>) {
  const { t } = useTranslation();

  return (
    <DialogFooter>
      <Button variant="outline" onClick={onClose}>
        {t('common.cancel')}
      </Button>
      <Button onClick={onSave} disabled={!canSubmit || pending}>
        {pending ? t('common.loading') : t('common.save')}
      </Button>
    </DialogFooter>
  );
}
