import { apiRequest } from './client';

export interface NewsItem {
  title: string;
  link: string;
  description?: string;
  source: string;
  language: string;
  publishedAt: string;
}

export const newsApi = {
  getLatest: (token: string, lang?: string, limit = 40) => {
    const params = new URLSearchParams({ limit: String(limit) });
    if (lang) params.set('lang', lang);
    return apiRequest<NewsItem[]>(`/news?${params.toString()}`, {}, token);
  },
};
