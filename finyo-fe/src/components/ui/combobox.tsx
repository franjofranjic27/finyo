import { useEffect, useId, useMemo, useRef, useState } from 'react';
import { cn } from '@/lib/utils';
import { Input } from '@/components/ui/input';

interface ComboboxEntry {
  readonly value: string;
  readonly isCreate: boolean;
}

export interface ComboboxProps {
  id?: string;
  value: string;
  onValueChange: (value: string) => void;
  /** Existing suggestion values; filtered case-insensitively while typing. */
  options: readonly string[];
  /**
   * When set, an extra "create" entry for the typed value is offered as long
   * as it matches no existing option exactly. The value stays free text either
   * way — the entry is only an explicit affordance.
   */
  formatCreateLabel?: (typedValue: string) => string;
  placeholder?: string;
  className?: string;
}

function buildEntries(
  options: readonly string[],
  query: string,
  withCreateEntry: boolean,
): ComboboxEntry[] {
  const normalizedQuery = query.trim().toLowerCase();
  const filtered = normalizedQuery
    ? options.filter((option) => option.toLowerCase().includes(normalizedQuery))
    : [...options];
  const entries: ComboboxEntry[] = filtered.map((option) => ({ value: option, isCreate: false }));

  const hasExactMatch = options.some((option) => option.toLowerCase() === normalizedQuery);
  if (withCreateEntry && normalizedQuery && !hasExactMatch) {
    entries.push({ value: query.trim(), isCreate: true });
  }
  return entries;
}

/**
 * Lightweight creatable combobox: a free-text input with a filtered suggestion
 * panel. Selection works via click or arrow keys + enter; typing any value not
 * in the list is always allowed (the input value IS the committed value).
 */
export function Combobox({
  id,
  value,
  onValueChange,
  options,
  formatCreateLabel,
  placeholder,
  className,
}: Readonly<ComboboxProps>) {
  const [open, setOpen] = useState(false);
  const [highlighted, setHighlighted] = useState(-1);
  const listboxId = useId();

  const entries = useMemo(
    () => buildEntries(options, value, formatCreateLabel !== undefined),
    [options, value, formatCreateLabel],
  );

  const showPanel = open && entries.length > 0;
  const optionId = (index: number) => `${listboxId}-opt-${index}`;

  // Radix dialogs listen for Escape on the document in the CAPTURE phase, so a
  // stopPropagation from the input's bubble-phase handler could never swallow
  // it. Instead we register our own capture listener on mount: child effects
  // run before parent effects, so ours is registered — and therefore invoked —
  // before the dialog's, and preventDefault makes Radix skip its dismiss.
  const showPanelRef = useRef(false);
  useEffect(() => {
    showPanelRef.current = showPanel;
  }, [showPanel]);

  useEffect(() => {
    const closePanelOnEscape = (event: KeyboardEvent) => {
      if (event.key !== 'Escape' || !showPanelRef.current) return;
      event.preventDefault();
      setOpen(false);
      setHighlighted(-1);
    };
    document.addEventListener('keydown', closePanelOnEscape, { capture: true });
    return () => document.removeEventListener('keydown', closePanelOnEscape, { capture: true });
  }, []);

  // Keep the highlighted option visible while arrowing through the
  // max-h-48 overflow list (no-op when it is already in view).
  useEffect(() => {
    if (highlighted < 0) return;
    // optional call — jsdom does not implement scrollIntoView
    document
      .getElementById(`${listboxId}-opt-${highlighted}`)
      ?.scrollIntoView?.({ block: 'nearest' });
  }, [highlighted, listboxId]);

  const select = (selectedValue: string) => {
    onValueChange(selectedValue);
    setOpen(false);
    setHighlighted(-1);
  };

  const handleKeyDown = (event: React.KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      if (entries.length === 0) return;
      setOpen(true);
      const delta = event.key === 'ArrowDown' ? 1 : -1;
      setHighlighted((current) => {
        // From the neutral state, ArrowUp starts at the end, ArrowDown at the top.
        if (current === -1) return delta === 1 ? 0 : entries.length - 1;
        return (current + delta + entries.length) % entries.length;
      });
      return;
    }
    if (event.key === 'Enter' && showPanel && highlighted >= 0 && highlighted < entries.length) {
      event.preventDefault();
      select(entries[highlighted].value);
    }
  };

  return (
    <div className={cn('relative', className)}>
      <Input
        id={id}
        role="combobox"
        aria-expanded={showPanel}
        aria-controls={listboxId}
        aria-autocomplete="list"
        aria-activedescendant={showPanel && highlighted >= 0 ? optionId(highlighted) : undefined}
        autoComplete="off"
        placeholder={placeholder}
        value={value}
        onChange={(event) => {
          onValueChange(event.target.value);
          setOpen(true);
          setHighlighted(-1);
        }}
        onFocus={() => setOpen(true)}
        onBlur={() => {
          setOpen(false);
          setHighlighted(-1);
        }}
        onKeyDown={handleKeyDown}
      />
      {showPanel && (
        <ul
          id={listboxId}
          role="listbox"
          className="absolute z-50 mt-1 max-h-48 w-full overflow-y-auto rounded-md border bg-popover p-1 text-popover-foreground shadow-md"
        >
          {entries.map((entry, index) => (
            <li
              key={`${entry.isCreate ? 'create' : 'option'}:${entry.value}`}
              id={optionId(index)}
              role="option"
              aria-selected={index === highlighted}
              className={cn(
                'cursor-pointer rounded-sm px-2 py-1.5 text-sm',
                index === highlighted && 'bg-accent text-accent-foreground',
                entry.isCreate && 'text-muted-foreground',
              )}
              // mousedown (not click) so selection wins over the input blur
              onMouseDown={(event) => {
                event.preventDefault();
                select(entry.value);
              }}
              onMouseEnter={() => setHighlighted(index)}
            >
              {entry.isCreate && formatCreateLabel ? formatCreateLabel(entry.value) : entry.value}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
