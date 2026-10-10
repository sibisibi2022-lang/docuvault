import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import type { Page, Role, UserProfile } from '../models/api.models';

@Injectable({ providedIn: 'root' })
export class UsersApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/users`;

  me(): Observable<UserProfile> {
    return this.http.get<UserProfile>(`${this.base}/me`);
  }

  updateProfile(name: string): Observable<UserProfile> {
    return this.http.put<UserProfile>(`${this.base}/me`, { name });
  }

  changePassword(currentPassword: string, newPassword: string): Observable<void> {
    return this.http.put<void>(`${this.base}/me/password`, { currentPassword, newPassword });
  }

  list(search: string | null, page = 0, size = 25): Observable<Page<UserProfile>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (search) params = params.set('search', search);
    return this.http.get<Page<UserProfile>>(this.base, { params });
  }

  changeRole(id: number, role: Role): Observable<UserProfile> {
    return this.http.patch<UserProfile>(`${this.base}/${id}/role`, { role });
  }

  setEnabled(id: number, enabled: boolean): Observable<UserProfile> {
    return this.http.patch<UserProfile>(`${this.base}/${id}/enabled`, { enabled });
  }
}
