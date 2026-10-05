import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { SesionService } from '../../core/sesion.service';
import { Login } from './login';

describe('Login', () => {
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      imports: [Login],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
  });

  it('no llama a la API si faltan datos', () => {
    const fixture = TestBed.createComponent(Login);
    fixture.componentInstance.ingresar();
    TestBed.inject(HttpTestingController).expectNone('/api/auth/login');
    expect(fixture.componentInstance.form.controls.username.touched).toBe(true);
  });

  it('con login correcto guarda la sesión y navega a la pantalla de inicio', () => {
    const fixture = TestBed.createComponent(Login);
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    fixture.componentInstance.form.setValue({ username: ' ana ', password: 'ana123' });
    fixture.componentInstance.ingresar();
    TestBed.inject(HttpTestingController)
      .expectOne('/api/auth/login')
      .flush({ token: 't', tipo: 'Bearer', rol: 'CLIENTE', venceEn: '2999-01-01T00:00:00Z' });

    expect(TestBed.inject(SesionService).usuario()).toBe('ana');
    expect(navegar).toHaveBeenCalledWith('/inicio');
  });

  it('no sigue un ?volver= hacia otro sitio (redirección abierta)', () => {
    const fixture = TestBed.createComponent(Login);
    fixture.componentRef.setInput('volver', '//sitio-malicioso.example');
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    fixture.componentInstance.form.setValue({ username: 'ana', password: 'ana123' });
    fixture.componentInstance.ingresar();
    TestBed.inject(HttpTestingController)
      .expectOne('/api/auth/login')
      .flush({ token: 't', tipo: 'Bearer', rol: 'CLIENTE', venceEn: '2999-01-01T00:00:00Z' });
    expect(navegar).toHaveBeenCalledWith('/inicio');
  });

  it('muestra el mensaje de la API en castellano (429 por demasiados intentos)', async () => {
    const fixture = TestBed.createComponent(Login);
    fixture.componentInstance.form.setValue({ username: 'ana', password: 'x' });
    fixture.componentInstance.ingresar();
    TestBed.inject(HttpTestingController)
      .expectOne('/api/auth/login')
      .flush(
        {
          detail: 'Demasiados intentos fallidos. Probá de nuevo en 15 minutos.',
          codigo: 'demasiados-intentos',
        },
        { status: 429, statusText: 'Too Many Requests' },
      );
    fixture.detectChanges();
    await fixture.whenStable();
    const alerta = (fixture.nativeElement as HTMLElement).querySelector('[role="alert"]');
    expect(alerta?.textContent).toContain('Probá de nuevo en 15 minutos');
    expect(fixture.componentInstance.enviando()).toBe(false);
  });
});
