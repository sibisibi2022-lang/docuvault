import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import type { Category, Page } from '../models/api.models';

@Injectable({ providedIn: 'root' })
export class CategoriesApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/categories`;

  list(page = 0, size = 100): Observable<Page<Category>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Page<Category>>(this.base, { params });
  }

  create(name: string, description: string | null): Observable<Category> {
    return this.http.post<Category>(this.base, { name, description });
  }

  update(id: number, name: string, description: string | null): Observable<Category> {
    return this.http.put<Category>(`${this.base}/${id}`, { name, description });
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
