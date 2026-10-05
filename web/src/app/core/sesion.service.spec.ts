import { TestBed } from '@angular/core/testing';
import { LoginResponse } from './modelos';
import { SesionService } from './sesion.service';

function respuesta(rol: 'CLIENTE' | 'OPERADOR', venceEn: string): LoginResponse {
  return { token: 'jwt-de-prueba', tipo: 'Bearer', rol, venceEn };
}

describe('SesionService', () => {
  beforeEach(() => sessionStorage.clear());

  it('arranca sin sesión', () => {
    const s = TestBed.inject(SesionService);
    expect(s.estaAutenticado()).toBe(false);
    expect(s.token()).toBeNull();
  });

  it('iniciar guarda token y rol, y los expone como signals', () => {
    const s = TestBed.inject(SesionService);
    s.iniciar('operador', respuesta('OPERADOR', '2999-01-01T00:00:00Z'));
    expect(s.estaAutenticado()).toBe(true);
    expect(s.token()).toBe('jwt-de-prueba');
    expect(s.esOperador()).toBe(true);
    expect(JSON.parse(sessionStorage.getItem('cuentas-web.sesion')!).usuario).toBe('operador');
  });

  it('una sesión vencida cuenta como no autenticada y se borra', () => {
    const s = TestBed.inject(SesionService);
    s.iniciar('ana', respuesta('CLIENTE', '2026-01-01T10:00:00Z'));
    expect(s.estaAutenticado(new Date('2026-01-01T09:59:00Z'))).toBe(true);
    expect(s.estaAutenticado(new Date('2026-01-01T10:00:01Z'))).toBe(false);
    expect(s.sesion()).toBeNull();
    expect(sessionStorage.getItem('cuentas-web.sesion')).toBeNull();
  });

  it('recupera la sesión guardada al recargar la página', () => {
    sessionStorage.setItem(
      'cuentas-web.sesion',
      JSON.stringify({
        token: 't',
        rol: 'CLIENTE',
        usuario: 'ana',
        venceEn: '2999-01-01T00:00:00Z',
      }),
    );
    const s = TestBed.inject(SesionService);
    expect(s.usuario()).toBe('ana');
  });

  it('ignora un dato guardado corrupto', () => {
    sessionStorage.setItem('cuentas-web.sesion', '{no es json');
    expect(TestBed.inject(SesionService).sesion()).toBeNull();
  });
});
