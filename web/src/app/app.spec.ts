import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';
import { SesionService } from './core/sesion.service';

describe('App', () => {
  beforeEach(async () => {
    sessionStorage.clear();
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([])],
    }).compileComponents();
  });

  function textoDelMenu(): string {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    return (fixture.nativeElement as HTMLElement).querySelector('header')?.textContent ?? '';
  }

  it('sin sesión no muestra el menú', () => {
    expect(textoDelMenu()).not.toContain('Salir');
  });

  it('un cliente ve "Mis cuentas" y "Transferir"', () => {
    TestBed.inject(SesionService).iniciar('ana', {
      token: 't',
      tipo: 'Bearer',
      rol: 'CLIENTE',
      venceEn: '2999-01-01T00:00:00Z',
    });
    const menu = textoDelMenu();
    expect(menu).toContain('Mis cuentas');
    expect(menu).toContain('Transferir');
    expect(menu).not.toContain('Clientes');
  });

  it('el operador ve sus pantallas y no la de transferir', () => {
    TestBed.inject(SesionService).iniciar('operador', {
      token: 't',
      tipo: 'Bearer',
      rol: 'OPERADOR',
      venceEn: '2999-01-01T00:00:00Z',
    });
    const menu = textoDelMenu();
    expect(menu).toContain('Clientes');
    expect(menu).not.toContain('Transferir');
  });
});
