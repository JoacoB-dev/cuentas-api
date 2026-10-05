import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  Router,
  RouterStateSnapshot,
  UrlTree,
  provideRouter,
} from '@angular/router';
import { authGuard, invitadoGuard, rolGuard } from './guards';
import { SesionService } from './sesion.service';

const ruta = {} as ActivatedRouteSnapshot;
const estado = { url: '/cuentas/1/movimientos' } as RouterStateSnapshot;

function ejecutar(guard: typeof authGuard): ReturnType<typeof authGuard> {
  return TestBed.runInInjectionContext(() => guard(ruta, estado));
}

function url(resultado: unknown): string {
  return TestBed.inject(Router).serializeUrl(resultado as UrlTree);
}

describe('guards', () => {
  let sesion: SesionService;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    sesion = TestBed.inject(SesionService);
  });

  function entrarComo(rol: 'CLIENTE' | 'OPERADOR'): void {
    sesion.iniciar('x', { token: 't', tipo: 'Bearer', rol, venceEn: '2999-01-01T00:00:00Z' });
  }

  it('authGuard sin sesión manda al login recordando a dónde quería ir', () => {
    expect(url(ejecutar(authGuard))).toBe('/login?volver=%2Fcuentas%2F1%2Fmovimientos');
  });

  it('authGuard con sesión deja pasar', () => {
    entrarComo('CLIENTE');
    expect(ejecutar(authGuard)).toBe(true);
  });

  it('rolGuard deja pasar al rol correcto y desvía al otro', () => {
    entrarComo('CLIENTE');
    expect(ejecutar(rolGuard('CLIENTE'))).toBe(true);
    expect(url(ejecutar(rolGuard('OPERADOR')))).toBe('/inicio');
  });

  it('invitadoGuard saca del login a quien ya tiene sesión', () => {
    expect(ejecutar(invitadoGuard)).toBe(true);
    entrarComo('OPERADOR');
    expect(url(ejecutar(invitadoGuard))).toBe('/inicio');
  });
});
