import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { RefreshCw, ExternalLink } from 'lucide-react';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { useAuth } from '@/auth/useAuth';
import { newsApi } from '@/api/news';

function relativeTime(isoDate: string): string {
  const diff = Date.now() - new Date(isoDate).getTime();
  const mins = Math.floor(diff / 60000);
  if (mins < 60) return `${mins}m ago`;
  const hours = Math.floor(mins / 60);
  if (hours < 24) return `${hours}h ago`;
  return new Date(isoDate).toLocaleDateString('de-CH');
}

type LangFilter = 'all' | 'en' | 'de';

export function News() {
  const { t } = useTranslation();
  const { accessToken } = useAuth();
  const token = accessToken ?? '';
  const [langFilter, setLangFilter] = useState<LangFilter>('all');

  const { data: articles, isLoading, isFetching, refetch } = useQuery({
    queryKey: ['news'],
    queryFn: () => newsApi.getLatest(token, undefined, 60),
    enabled: !!token,
    refetchInterval: 600_000,
  });

  const filtered = (articles ?? []).filter(
    (a) => langFilter === 'all' || a.language === langFilter,
  );

  const LANG_LABELS: Record<LangFilter, string> = { all: 'All', en: 'EN', de: 'DE' };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">{t('news.title')}</h1>
        <div className="flex items-center gap-2">
          {/* Language filter */}
          <div className="flex rounded-md border divide-x overflow-hidden">
            {(['all', 'en', 'de'] as LangFilter[]).map((lang) => (
              <button
                key={lang}
                type="button"
                onClick={() => setLangFilter(lang)}
                className={`px-3 py-1.5 text-sm transition-colors ${
                  langFilter === lang
                    ? 'bg-primary text-primary-foreground'
                    : 'bg-background text-muted-foreground hover:bg-muted'
                }`}
              >
                {LANG_LABELS[lang]}
              </button>
            ))}
          </div>
          <Button variant="outline" size="sm" onClick={() => refetch()} disabled={isFetching}>
            <RefreshCw className={`h-4 w-4 ${isFetching ? 'animate-spin' : ''}`} />
          </Button>
        </div>
      </div>

      {isLoading ? (
        <div className="space-y-3">
          {Array.from({ length: 8 }).map((_, i) => (
            <Card key={i}>
              <CardContent className="pt-4 space-y-2">
                <Skeleton className="h-4 w-24" />
                <Skeleton className="h-5 w-full" />
                <Skeleton className="h-4 w-3/4" />
              </CardContent>
            </Card>
          ))}
        </div>
      ) : filtered.length === 0 ? (
        <div className="py-16 text-center text-muted-foreground">{t('news.noNews')}</div>
      ) : (
        <div className="space-y-3">
          {filtered.map((article, i) => (
            <Card key={i} className="hover:bg-muted/30 transition-colors">
              <CardContent className="pt-4 pb-4">
                <div className="flex items-start justify-between gap-4">
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2 mb-1.5">
                      <Badge variant="secondary" className="text-xs shrink-0">
                        {article.source}
                      </Badge>
                      <Badge
                        variant="outline"
                        className="text-xs shrink-0 uppercase"
                      >
                        {article.language}
                      </Badge>
                      <span className="text-xs text-muted-foreground shrink-0">
                        {relativeTime(article.publishedAt)}
                      </span>
                    </div>
                    <a
                      href={article.link}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="font-medium text-sm hover:underline leading-snug block"
                    >
                      {article.title}
                    </a>
                    {article.description && (
                      <p className="mt-1 text-xs text-muted-foreground line-clamp-2">
                        {article.description}
                      </p>
                    )}
                  </div>
                  <a
                    href={article.link}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="shrink-0 text-muted-foreground hover:text-foreground"
                    aria-label={t('news.readMore')}
                  >
                    <ExternalLink className="h-4 w-4" />
                  </a>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
