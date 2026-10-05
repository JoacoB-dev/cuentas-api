import { inject } from '@angular/core';
import { Routes } from '@angular/router';
import { authGuard, invitadoGuard, rolGuard } from './core/guards';
import { SesionService } from './core/sesion.service';

/** Pantalla de inicio según el rol (el guard de cada ruta manda al login si no hay sesión). */
const inicioSegunRol = () =>
  inject(SesionService).esOperador() ? '/operador/cuentas' : '/cuentas';

// Las pantallas se cargan de forma diferida (lazy): el cliente no descarga las del operador.
export const routes: Routes = [
  {
    path: 'login',
    canActivate: [invitadoGuard],
    title: 'Ingresar',
    loadComponent: () => import('./paginas/login/login').then((m) => m.Login),
  },
  {
    // Pantalla de inicio según el rol.
    path: 'inicio',
    redirectTo: inicioSegunRol,
  },
  {
    path: 'cuentas',
    canActivate: [authGuard],
    title: 'Mis cuentas',
    loadComponent: () => import('./paginas/cuentas/mis-cuentas').then((m) => m.MisCuentas),
  },
  {
    path: 'cuentas/:id/movimientos',
    canActivate: [authGuard],
    title: 'Movimientos',
    loadComponent: () => import('./paginas/cuentas/movimientos').then((m) => m.Movimientos),
  },
  {
    path: 'cuentas/:id/extracto',
    canActivate: [authGuard],
    title: 'Extracto',
    loadComponent: () => import('./paginas/cuentas/extracto').then((m) => m.ExtractoCuenta),
  },
  {
    path: 'transferir',
    canActivate: [rolGuard('CLIENTE')],
    title: 'Transferir',
    loadComponent: () => import('./paginas/transferencias/transferir').then((m) => m.Transferir),
  },
  {
    path: 'operador/cuentas',
    canActivate: [rolGuard('OPERADOR')],
    title: 'Cuentas (operador)',
    loadComponent: () =>
      import('./paginas/operador/cuentas-operador').then((m) => m.CuentasOperador),
  },
  {
    path: 'operador/clientes',
    canActivate: [rolGuard('OPERADOR')],
    title: 'Clientes (operador)',
    loadComponent: () => import('./paginas/operador/clientes').then((m) => m.Clientes),
  },
  // "/" y "**" van directo a la función (y no a 'inicio'): encadenar una redirección fija con una
  // redirección por función dejaba la navegación inicial de "/" colgada.
  { path: '', pathMatch: 'full', redirectTo: inicioSegunRol },
  { path: '**', redirectTo: inicioSegunRol },
];
