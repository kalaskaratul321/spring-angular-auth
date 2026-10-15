import { Routes } from '@angular/router';

import { authGuard } from './core/auth.guard';

export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./pages/login/login').then((m) => m.Login) },
  {
    path: 'overview',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/overview/overview').then((m) => m.Overview),
  },
  {
    path: 'explorer',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/explorer/explorer').then((m) => m.Explorer),
  },
  {
    path: 'courts',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/courts/courts').then((m) => m.Courts),
  },
  { path: '', pathMatch: 'full', redirectTo: 'overview' },
  { path: '**', redirectTo: 'overview' },
];
