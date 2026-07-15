import { apiRequest } from './client';

export type DocumentStatus =
  | 'DISCOVERED'
  | 'ANALYZED'
  | 'AUTO_APPLIED'
  | 'NEEDS_REVIEW'
  | 'APPLIED'
  | 'DISMISSED'
  | 'FAILED';

export type TaxDocumentType =
  | 'SALARY_CERTIFICATE'
  | 'HEALTH_INSURANCE'
  | 'SECURITIES_STATEMENT'
  | 'PILLAR_3A'
  | 'ASSESSMENT'
  | 'UNKNOWN';

/** One value read from the document; only mapped fields can be applied. */
export interface ExtractedField {
  fieldName: string;
  /** User-presentable label, e.g. "Bruttolohn total (Ziffer 8)". */
  label: string;
  value: number;
  /** Target tax-year input field, or null for info-only values. */
  targetTaxYearField: string | null;
}

export interface DocumentResponse {
  id: string;
  filename: string;
  sourcePath: string;
  status: DocumentStatus;
  /** Type derived from the PDF content. */
  detectedType: TaxDocumentType | null;
  /** Type derived from the folder the document sits in. */
  folderType: TaxDocumentType | null;
  /** 0..1 — display only, never a basis for an automated decision. */
  confidence: number | null;
  detectedTaxYear: number | null;
  /** Tax year derived from the folder path, e.g. STE-2025. */
  folderTaxYear: number | null;
  extractedFields: ExtractedField[];
  failureReason: string | null;
  deletedInDrive: boolean;
  createdAt: string;
}

export interface DocumentSyncResult {
  autoApplied: number;
  needsReview: number;
  failed: number;
}

export interface ApplyDocumentRequest {
  year: number;
  /** Empty means "all mapped fields" — send explicit names to apply a subset. */
  fieldNames: string[];
}

export const documentsApi = {
  getAll: (token: string) => apiRequest<DocumentResponse[]>('/documents', {}, token),

  sync: (token: string) =>
    apiRequest<DocumentSyncResult>('/documents/sync', { method: 'POST' }, token),

  apply: (token: string, id: string, request: ApplyDocumentRequest) =>
    apiRequest<DocumentResponse>(
      `/documents/${id}/apply`,
      { method: 'POST', body: JSON.stringify(request) },
      token,
    ),

  dismiss: (token: string, id: string) =>
    apiRequest<DocumentResponse>(`/documents/${id}/dismiss`, { method: 'POST' }, token),
};
