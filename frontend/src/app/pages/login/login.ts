import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';

import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-login',
  imports: [FormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main class="login">
      <form class="card" (ngSubmit)="submit()" #f="ngForm">
        <h1>MahaLegal knowledge graph</h1>
        <p class="muted">Sign in with your MahaLegal account.</p>
        <label>
          Email
          <input name="email" type="email" autocomplete="username" required [(ngModel)]="email" />
        </label>
        <label>
          Password
          <input name="password" type="password" autocomplete="current-password" required [(ngModel)]="password" />
        </label>
        @if (error()) {
          <p class="error" role="alert">{{ error() }}</p>
        }
        <button type="submit" class="primary" [disabled]="busy() || !f.valid">
          {{ busy() ? 'Signing in…' : 'Sign in' }}
        </button>
      </form>
    </main>
  `,
  styles: `
    .login { min-height: 100dvh; display: grid; place-items: center; padding: 16px; }
    .card { width: min(380px, 100%); display: grid; gap: 14px; }
    h1 { font-size: 1.3rem; margin: 0; }
    label { display: grid; gap: 6px; font-weight: 500; }
  `,
})
export class Login {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected email = '';
  protected password = '';
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  submit(): void {
    this.busy.set(true);
    this.error.set(null);
    this.auth.login(this.email, this.password).subscribe({
      next: () => this.router.navigate(['/overview']),
      error: (e: HttpErrorResponse) => {
        this.busy.set(false);
        this.error.set(e.status === 0 ? 'Cannot reach the server.' : 'Invalid email or password.');
      },
    });
  }
}
