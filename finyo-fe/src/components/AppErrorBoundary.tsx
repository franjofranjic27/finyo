import { Component, type ErrorInfo, type ReactNode } from 'react';
import { withTranslation, type WithTranslation } from 'react-i18next';
import { Button } from '@/components/ui/button';

interface Props extends WithTranslation {
  readonly children: ReactNode;
}

interface State {
  hasError: boolean;
}

/**
 * Last line of defence: a render crash anywhere below must never leave the user
 * on a blank page. The prime real-world cause is version skew — a stale
 * PWA-precached shell running against a newer API — which a reload resolves,
 * because the service worker has fetched the new shell in the background by then.
 */
class Boundary extends Component<Props, State> {
  state: State = { hasError: false };

  static getDerivedStateFromError(): State {
    return { hasError: true };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('Unhandled render error', error, info.componentStack);
  }

  render() {
    if (!this.state.hasError) {
      return this.props.children;
    }
    const { t } = this.props;
    return (
      <div className="flex min-h-screen flex-col items-center justify-center gap-4 bg-background p-6 text-center text-foreground">
        <h1 className="text-lg font-semibold">{t('errorBoundary.title')}</h1>
        <p className="max-w-md text-sm text-muted-foreground">{t('errorBoundary.hint')}</p>
        <Button onClick={() => window.location.reload()}>{t('errorBoundary.reload')}</Button>
      </div>
    );
  }
}

export const AppErrorBoundary = withTranslation()(Boundary);
