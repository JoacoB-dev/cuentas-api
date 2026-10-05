import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Cuenta } from '../../core/modelos';
import { Transferir } from './transferir';

const CUENTA_ANA: Cuenta = {
  id: 1,
  cbu: '9990001800000000010001',
  tipo: 'CA',
  moneda: 'ARS',
  estado: 'ACTIVA',
  saldo: 500000,
  disponible: 500000,
  descubiertoAutorizado: 0,
  limiteDiario: 1000000,
  clienteId: 1,
  titular: 'Ana Gómez',
};
const CBU_BRUNO = '9990001800000000010025';

describe('Transferir', () => {
  let fixture: ComponentFixture<Transferir>;
  let componente: Transferir;
  let backend: HttpTestingController;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [Transferir],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    fixture = TestBed.createComponent(Transferir);
    componente = fixture.componentInstance;
    backend = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('origen', '1');
    fixture.detectChanges();
    backend.expectOne('/api/cuentas').flush([CUENTA_ANA]);
    await fixture.whenStable();
  });

  afterEach(() => backend.verify());

  function completar(importe = 1500): void {
    componente.form.patchValue({ cbuDestino: CBU_BRUNO, importe, concepto: 'Asado' });
    componente.continuar();
  }

  function respuestaOk(replayed = false) {
    return {
      body: {
        id: 7,
        cuentaOrigenId: 1,
        cuentaDestinoId: 3,
        importe: 1500,
        moneda: 'ARS',
        concepto: 'Asado',
        fecha: '',
      },
      opts: {
        status: 201,
        statusText: 'Created',
        headers: { 'Idempotent-Replayed': String(replayed) },
      },
    };
  }

  it('preselecciona la cuenta de ?origen= y no avanza con datos inválidos', () => {
    expect(componente.form.controls.cuentaOrigenId.value).toBe(1);
    componente.form.patchValue({ cbuDestino: '123', importe: 0 });
    componente.continuar();
    expect(componente.paso()).toBe('formulario');
  });

  it('pide confirmación y envía la transferencia con una Idempotency-Key', async () => {
    completar();
    expect(componente.paso()).toBe('confirmacion');
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Revisá los datos');

    componente.confirmar();
    const req = backend.expectOne('/api/transferencias');
    expect(req.request.method).toBe('POST');
    expect(req.request.headers.get('Idempotency-Key')).toBe(componente.clave());
    expect(req.request.body).toEqual({
      cuentaOrigenId: 1,
      cbuDestino: CBU_BRUNO,
      importe: 1500,
      concepto: 'Asado',
    });
    const r = respuestaOk();
    req.flush(r.body, r.opts);

    expect(componente.paso()).toBe('hecha');
    expect(componente.repetida()).toBe(false);
  });

  it('si se corta la conexión, el reintento usa LA MISMA clave', () => {
    completar();
    const clave = componente.clave();

    componente.confirmar();
    backend.expectOne('/api/transferencias').error(new ProgressEvent('error'), { status: 0 });
    expect(componente.paso()).toBe('confirmacion');
    expect(componente.resultadoIncierto()).toBe(true);

    componente.confirmar();
    const reintento = backend.expectOne('/api/transferencias');
    expect(reintento.request.headers.get('Idempotency-Key')).toBe(clave);
    const r = respuestaOk(true);
    reintento.flush(r.body, r.opts);
    expect(componente.repetida()).toBe(true);
  });

  it('si el usuario vuelve a editar, la próxima transferencia usa una clave nueva', () => {
    completar();
    const primera = componente.clave();
    componente.modificar();
    completar(2000);
    expect(componente.clave()).not.toBe(primera);
  });

  it('muestra el error de negocio de la API (422) sin perder los datos', async () => {
    completar();
    componente.confirmar();
    backend
      .expectOne('/api/transferencias')
      .flush(
        {
          detail: 'Saldo insuficiente en la cuenta 9990001800000000010001.',
          codigo: 'saldo-insuficiente',
        },
        { status: 422, statusText: 'Unprocessable Entity' },
      );
    fixture.detectChanges();
    await fixture.whenStable();
    expect(componente.resultadoIncierto()).toBe(false);
    expect(
      (fixture.nativeElement as HTMLElement).querySelector('[role="alert"]')?.textContent,
    ).toContain('Saldo insuficiente');
  });
});
