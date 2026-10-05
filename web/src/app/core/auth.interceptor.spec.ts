import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { authInterceptor } from './auth.interceptor';
import { SesionService } from './sesion.service';

describe('authInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let sesion: SesionService;

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
    backend = TestBed.inject(HttpTestingController);
    sesion = TestBed.inject(SesionService);
    sesion.iniciar('ana', {
      token: 'abc.def.ghi',
      tipo: 'Bearer',
      rol: 'CLIENTE',
      venceEn: '2999-01-01T00:00:00Z',
    });
  });

  afterEach(() => backend.verify());

  it('agrega el header Authorization a los pedidos a la API', () => {
    http.get('/api/cuentas').subscribe();
    const req = backend.expectOne('/api/cuentas');
    expect(req.request.headers.get('Authorization')).toBe('Bearer abc.def.ghi');
    req.flush([]);
  });

  it('no manda el token al login ni a URLs que no son de la API', () => {
    http.post('/api/auth/login', {}).subscribe();
    http.get('https://otro-sitio.example/datos').subscribe();
    expect(backend.expectOne('/api/auth/login').request.headers.has('Authorization')).toBe(false);
    expect(
      backend.expectOne('https://otro-sitio.example/datos').request.headers.has('Authorization'),
    ).toBe(false);
  });

  it('ante un 401 cierra la sesión y lleva al login', () => {
    const router = TestBed.inject(Router);
    const navegar = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    let fallo = false;
    http.get('/api/cuentas').subscribe({ error: () => (fallo = true) });
    backend
      .expectOne('/api/cuentas')
      .flush({ codigo: 'no-autenticado' }, { status: 401, statusText: 'Unauthorized' });

    expect(fallo).toBe(true);
    expect(sesion.sesion()).toBeNull();
    expect(navegar).toHaveBeenCalledWith(['/login'], { queryParams: { motivo: 'sesion-vencida' } });
  });

  it('un 401 del login (contraseña mala) no redirige', () => {
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate');
    http.post('/api/auth/login', {}).subscribe({ error: () => undefined });
    backend.expectOne('/api/auth/login').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(navegar).not.toHaveBeenCalled();
  });
});
