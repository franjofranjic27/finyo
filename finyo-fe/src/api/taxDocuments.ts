// Multipart uploads bypass apiRequest on purpose: the shared client hardcodes
// Content-Type application/json, while FormData needs the browser-generated
// multipart boundary (no explicit Content-Type header at all).

const BASE_URL = '/api/v1';

export type TaxDocumentType =
  | 'SALARY_CERTIFICATE'
  | 'HEALTH_INSURANCE'
  | 'SECURITIES_STATEMENT'
  | 'PILLAR_3A'
  | 'ASSESSMENT';

export type DetectedDocumentType = TaxDocumentType | 'UNKNOWN';

export interface DocumentClassification {
  detectedType: DetectedDocumentType;
  /** 0..1 */
  confidence: number;
}

/** One extracted value the backend can map onto a tax-year input field. */
export interface ExtractedFieldMapping {
  fieldName: string;
  /** User-presentable German label, e.g. "Bruttolohn total (Ziffer 8)". */
  label: string;
  value: number;
  targetTaxYearField: string;
}

export interface DocumentExtraction {
  type: TaxDocumentType;
  taxYear: number | null;
  /** All extracted raw fields; entries without a mapping are info-only. */
  fields: Record<string, number | string | null>;
  taxYearMapping: ExtractedFieldMapping[];
}

/** RFC-7807 ProblemDetail error with the document-processing extension. */
export class TaxDocumentError extends Error {
  readonly status: number;
  /** Set on 422 when the document was classified as a different type. */
  readonly detectedType?: TaxDocumentType;

  constructor(message: string, status: number, detectedType?: TaxDocumentType) {
    super(message);
    this.name = 'TaxDocumentError';
    this.status = status;
    this.detectedType = detectedType;
  }
}

interface ProblemDetail {
  detail?: string;
  detectedType?: string;
}

const EXTRACTION_PATHS: Record<TaxDocumentType, string> = {
  SALARY_CERTIFICATE: '/tax/documents/salary-certificate',
  HEALTH_INSURANCE: '/tax/documents/health-insurance',
  SECURITIES_STATEMENT: '/tax/documents/securities-statement',
  PILLAR_3A: '/tax/documents/pillar3a',
  ASSESSMENT: '/tax/documents/assessment',
};

function isTaxDocumentType(value: string | undefined): value is TaxDocumentType {
  return value != null && value in EXTRACTION_PATHS;
}

async function postDocument<T>(path: string, token: string, file: File): Promise<T> {
  const body = new FormData();
  body.append('file', file);

  const response = await fetch(`${BASE_URL}${path}`, {
    method: 'POST',
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body,
  });

  if (!response.ok) {
    const problem = (await response.json().catch(() => null)) as ProblemDetail | null;
    throw new TaxDocumentError(
      problem?.detail ?? `Request failed: ${response.status}`,
      response.status,
      isTaxDocumentType(problem?.detectedType) ? problem.detectedType : undefined,
    );
  }

  return response.json() as Promise<T>;
}

export function classifyDocument(token: string, file: File): Promise<DocumentClassification> {
  return postDocument<DocumentClassification>('/tax/documents/classify', token, file);
}

export function extractDocument(
  token: string,
  type: TaxDocumentType,
  file: File,
): Promise<DocumentExtraction> {
  return postDocument<DocumentExtraction>(EXTRACTION_PATHS[type], token, file);
}
