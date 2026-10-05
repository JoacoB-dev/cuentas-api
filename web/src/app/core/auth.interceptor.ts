import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { SesionService } from './sesion.service';

const URL_LOGIN = '/api/auth/login';

/**
 * Agrega el JWT a cada pedido a la API. Si la API responde 401 (token vencido o
 * inválido), cierra la sesión y manda al login avisando el motivo.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const sesion = inject(SesionService);
  const router = inject(Router);
  const esLogin = req.url.endsWith(URL_LOGIN);
  const token = sesion.token();

  const pedido =
    token && !esLogin && req.url.startsWith('/api/')
      ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
      : req;

  return next(pedido).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401 && !esLogin) {
        sesion.cerrar();
        void router.navigate(['/login'], { queryParams: { motivo: 'sesion-vencida' } });
      }
      return throwError(() => error);
    }),
  );
};
