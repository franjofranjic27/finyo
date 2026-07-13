import { Link } from 'react-router-dom';

interface CardEmptyStateProps {
  message: string;
  ctaLabel: string;
  /** Router target of the call to action, e.g. `/budget?tab=month`. */
  to: string;
}

/** Empty state inside a card: explanation plus a link to fix it. */
export function CardEmptyState({ message, ctaLabel, to }: Readonly<CardEmptyStateProps>) {
  return (
    <div className="flex flex-col items-center gap-2 py-10 text-center">
      <p className="text-sm text-muted-foreground">{message}</p>
      <Link to={to} className="text-sm font-medium text-primary underline-offset-2 hover:underline">
        {ctaLabel} ↗
      </Link>
    </div>
  );
}
