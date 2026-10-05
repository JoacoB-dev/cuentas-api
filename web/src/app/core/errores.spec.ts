import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, FormGroup } from '@angular/forms';
import { marcarErroresDelServidor, mensajePorStatus, traducirError } from './errores';

describe('traducirError', () => {
  it('usa el detail del ProblemDetail de la API tal cual', () => {
    const e = new HttpErrorResponse({
      status: 422,
      error: {
        status: 422,
        detail: 'Saldo insuficiente en la cuenta X.',
        codigo: 'saldo-insuficiente',
      },
    });
    const r = traducirError(e);
    expect(r.mensaje).toBe('Saldo insuficiente en la cuenta X.');
    expect(r.codigo).toBe('saldo-insuficiente');
    expect(r.status).toBe(422);
  });

  it('arma el mapa de errores por campo de un 400 de validación', () => {
    const e = new HttpErrorResponse({
      status: 400,
      error: {
        detail: 'Hay campos con errores.',
        codigo: 'validacion',
        errores: [{ campo: 'dni', mensaje: 'El DNI debe tener 7 u 8 dígitos, sin puntos' }],
      },
    });
    expect(traducirError(e).campos).toEqual({ dni: 'El DNI debe tener 7 u 8 dígitos, sin puntos' });
  });

  it('sin conexión (status 0) da un mensaje en castellano', () => {
    const r = traducirError(
      new HttpErrorResponse({ status: 0, error: new ProgressEvent('error') }),
    );
    expect(r.mensaje).toContain('No se pudo conectar');
    expect(r.codigo).toBeNull();
  });

  it('si el cuerpo no es ProblemDetail (por ejemplo, HTML de un proxy) usa el mensaje por status', () => {
    const r = traducirError(
      new HttpErrorResponse({ status: 502, error: '<html>Bad Gateway</html>' }),
    );
    expect(r.mensaje).toBe(mensajePorStatus(502));
  });

  it('un error que no es HTTP también se traduce', () => {
    expect(traducirError(new Error('x')).mensaje).toBe('Ocurrió un error inesperado.');
  });
});

describe('marcarErroresDelServidor', () => {
  it('pega el mensaje en el control correspondiente y lo marca como tocado', () => {
    const form = new FormGroup({ dni: new FormControl('123'), email: new FormControl('a@b.com') });
    marcarErroresDelServidor(form, {
      mensaje: 'x',
      codigo: 'validacion',
      status: 400,
      campos: { dni: 'DNI inválido', inexistente: 'se ignora' },
    });
    expect(form.controls.dni.errors).toEqual({ servidor: 'DNI inválido' });
    expect(form.controls.dni.touched).toBe(true);
    expect(form.controls.email.errors).toBeNull();
  });
});
