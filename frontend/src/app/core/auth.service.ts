import { HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';

import { LoginResponse } from './api.models';

const STORAGE_KEY = 'mahalegal.session';

/**
 * JWT session against the existing /api/auth endpoints. The token is kept in
 * sessionStorage so it is dropped when the browser tab is closed.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly session = signal<LoginResponse | null>(AuthService.restore());

  readonly user = this.session.asReadonly();
  readonly isLoggedIn = computed(() => this.session() !== null);
  readonly isAdmin = computed(() => this.session()?.role === 'Admin');

  get token(): string | null {
    return this.session()?.token ?? null;
  }

  login(email: string, password: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>('/api/auth/login', { email, password }).pipe(
      tap((res) => {
        this.session.set(res);
        try {
          sessionStorage.setItem(STORAGE_KEY, JSON.stringify(res));
        } catch {
          // storage unavailable (private mode): keep the session in memory only
        }
      }),
    );
  }

  logout(redirect = true): void {
    this.session.set(null);
    try {
      sessionStorage.removeItem(STORAGE_KEY);
    } catch {
      // ignore
    }
    if (redirect) {
      this.router.navigate(['/login']);
    }
  }

  private static restore(): LoginResponse | null {
    try {
      const raw = sessionStorage.getItem(STORAGE_KEY);
      return raw ? (JSON.parse(raw) as LoginResponse) : null;
    } catch {
      return null;
    }
  }
}
