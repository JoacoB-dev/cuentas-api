import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Rol } from './modelos';
import { SesionService } from './sesion.service';

/** Deja pasar sólo con sesión iniciada y vigente; si no, al login. */
export const authGuard: CanActivateFn = (_ruta, estado) => {
  const sesion = inject(SesionService);
  const router = inject(Router);
  if (sesion.estaAutenticado()) {
    return true;
  }
  return router.createUrlTree(['/login'], { queryParams: { volver: estado.url } });
};

/**
 * Deja pasar sólo a un rol. Es una ayuda de navegación, no seguridad: la API
 * controla los permisos de verdad (responde 403 aunque alguien fuerce la ruta).
 */
export function rolGuard(rol: Rol): CanActivateFn {
  return () => {
    const sesion = inject(SesionService);
    const router = inject(Router);
    if (!sesion.estaAutenticado()) {
      return router.createUrlTree(['/login']);
    }
    return sesion.rol() === rol ? true : router.createUrlTree(['/inicio']);
  };
}

/** Si ya hay sesión, no tiene sentido mostrar el login. */
export const invitadoGuard: CanActivateFn = () => {
  const sesion = inject(SesionService);
  const router = inject(Router);
  return sesion.estaAutenticado() ? router.createUrlTree(['/inicio']) : true;
};
