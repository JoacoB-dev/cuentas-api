import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { routes } from './app.routes';
import { SesionService } from './core/sesion.service';

// Redirecciones de la raíz: "/" tiene que terminar en el login o en el inicio del rol.
describe('rutas', () => {
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter(routes)] });
  });

  it('"/" sin sesión lleva al login', async () => {
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/');
    expect(router.url).toMatch(/^\/login/);
  });

  it('"/" con sesión de operador lleva a sus cuentas', async () => {
    TestBed.inject(SesionService).iniciar('operador', {
      token: 't',
      tipo: 'Bearer',
      rol: 'OPERADOR',
      venceEn: '2999-01-01T00:00:00Z',
    });
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/');
    expect(router.url).toBe('/operador/cuentas');
  });

  it('una ruta desconocida con sesión de cliente lleva a "Mis cuentas"', async () => {
    TestBed.inject(SesionService).iniciar('ana', {
      token: 't',
      tipo: 'Bearer',
      rol: 'CLIENTE',
      venceEn: '2999-01-01T00:00:00Z',
    });
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/no-existe');
    expect(router.url).toBe('/cuentas');
  });
});
