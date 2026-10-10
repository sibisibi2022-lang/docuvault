import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import type {
  BulkResult,
  DashboardStats,
  DocumentSummary,
  DocumentVersion,
  Page,
} from '../models/api.models';

export interface DocumentFilters {
  query?: string;
  categoryId?: number | null;
  contentType?: string | null;
  page?: number;
  size?: number;
}

export interface DocumentMetadata {
  title: string;
  description: string | null;
  categoryId: number | null;
}

@Injectable({ providedIn: 'root' })
export class DocumentsApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/documents`;

  list(filters: DocumentFilters = {}): Observable<Page<DocumentSummary>> {
    let params = new HttpParams()
      .set('page', filters.page ?? 0)
      .set('size', filters.size ?? 20);

    if (filters.query) params = params.set('query', filters.query);
    if (filters.categoryId) params = params.set('categoryId', filters.categoryId);
    if (filters.contentType) params = params.set('contentType', filters.contentType);

    return this.http.get<Page<DocumentSummary>>(this.base, { params });
  }

  stats(): Observable<DashboardStats> {
    return this.http.get<DashboardStats>(`${this.base}/stats`);
  }

  get(id: number): Observable<DocumentSummary> {
    return this.http.get<DocumentSummary>(`${this.base}/${id}`);
  }

  upload(metadata: DocumentMetadata, file: File): Observable<DocumentSummary> {
    return this.http.post<DocumentSummary>(this.base, toFormData(metadata, file));
  }

  /** Omitting the file updates metadata only; supplying one creates a version. */
  update(id: number, metadata: DocumentMetadata, file?: File | null): Observable<DocumentSummary> {
    return this.http.put<DocumentSummary>(`${this.base}/${id}`, toFormData(metadata, file));
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }

  versions(id: number, page = 0, size = 50): Observable<Page<DocumentVersion>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Page<DocumentVersion>>(`${this.base}/${id}/versions`, { params });
  }

  restoreVersion(documentId: number, versionId: number): Observable<DocumentSummary> {
    return this.http.post<DocumentSummary>(
      `${this.base}/${documentId}/versions/${versionId}/restore`,
      {},
    );
  }

  // --------------------------------------------------------------- bulk

  bulkDelete(ids: number[]): Observable<BulkResult> {
    return this.http.post<BulkResult>(`${this.base}/bulk-delete`, { ids });
  }

  bulkMoveToCategory(ids: number[], categoryId: number | null): Observable<BulkResult> {
    return this.http.patch<BulkResult>(`${this.base}/bulk-category`, { ids, categoryId });
  }

  bulkDownload(ids: number[]): Observable<Blob> {
    return this.http.post(`${this.base}/bulk-download`, { ids }, { responseType: 'blob' });
  }

  /**
   * Downloads as a blob rather than pointing the browser at the URL directly.
   *
   * <p>A plain anchor cannot carry the Authorization header, so a direct link
   * would need the API to accept the token in the query string — where it would
   * end up in server logs and browser history.
   */
  download(id: number): Observable<Blob> {
    return this.http.get(`${this.base}/${id}/download`, { responseType: 'blob' });
  }

  downloadVersion(versionId: number): Observable<Blob> {
    return this.http.get(`${environment.apiBaseUrl}/document-versions/${versionId}/download`, {
      responseType: 'blob',
    });
  }
}

/**
 * The API takes metadata as a JSON part beside the binary part, so validation
 * annotations apply to it exactly as they would on a JSON body.
 */
function toFormData(metadata: DocumentMetadata, file?: File | null): FormData {
  const form = new FormData();
  form.append(
    'metadata',
    new Blob([JSON.stringify(metadata)], { type: 'application/json' }),
  );
  if (file) {
    form.append('file', file);
  }
  return form;
}
