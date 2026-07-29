import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { HasId } from '../models/dtos';

export type QueryParams = Record<string, string | number | boolean | null | undefined>;

/**
 * One generic REST client for the whole app. Every backend resource follows the
 * same CRUD contract (POST / GET / GET{id} / PUT{id} / DELETE{id}), so a single
 * service — parameterised by the resource's base path — covers all of them.
 */
@Injectable({ providedIn: 'root' })
export class CrudService {
  private http = inject(HttpClient);

  list<T extends HasId>(base: string, params?: QueryParams): Observable<T[]> {
    return this.http.get<T[]>(base, { params: this.toParams(params) });
  }

  get<T extends HasId>(base: string, id: number): Observable<T> {
    return this.http.get<T>(`${base}/${id}`);
  }

  create<T extends HasId>(base: string, body: unknown): Observable<T> {
    return this.http.post<T>(base, body);
  }

  update<T extends HasId>(base: string, id: number, body: unknown): Observable<T> {
    return this.http.put<T>(`${base}/${id}`, body);
  }

  remove(base: string, id: number): Observable<void> {
    return this.http.delete<void>(`${base}/${id}`);
  }

  /** For the notification read/dismiss transitions (and any future PATCH verb). */
  patch<T extends HasId>(fullPath: string): Observable<T> {
    return this.http.patch<T>(fullPath, {});
  }

  private toParams(params?: QueryParams): HttpParams {
    let p = new HttpParams();
    if (params) {
      for (const [key, value] of Object.entries(params)) {
        if (value !== null && value !== undefined && value !== '') {
          p = p.set(key, String(value));
        }
      }
    }
    return p;
  }
}
