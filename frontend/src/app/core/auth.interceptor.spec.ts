import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';

import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';

describe('authInterceptor', () => {
  let http: HttpClient;
  let ctrl: HttpTestingController;
  let auth: AuthService;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    ctrl = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
  });

  afterEach(() => {
    ctrl.verify();
    sessionStorage.clear();
  });

  function login(): void {
    auth.login('a@b.in', 'pw').subscribe();
    ctrl.expectOne('/api/auth/login').flush({ username: 'a@b.in', token: 'jwt-123', role: 'Admin' });
  }

  it('adds the bearer token after login and not on the login call', () => {
    login();
    http.get('/api/kg/categories').subscribe();
    expect(ctrl.expectOne('/api/kg/categories').request.headers.get('Authorization')).toBe('Bearer jwt-123');
    expect(auth.isAdmin()).toBeTrue();
  });

  it('logs out and goes to /login on 401', () => {
    login();
    const router = TestBed.inject(Router);
    const nav = spyOn(router, 'navigate').and.resolveTo(true);
    http.get('/api/kg/categories').subscribe({ error: () => undefined });
    ctrl.expectOne('/api/kg/categories').flush('', { status: 401, statusText: 'Unauthorized' });
    expect(auth.isLoggedIn()).toBeFalse();
    expect(nav).toHaveBeenCalledWith(['/login']);
  });
});
