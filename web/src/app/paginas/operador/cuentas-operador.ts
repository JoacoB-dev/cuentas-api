import { CurrencyPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { ErrorApi, marcarErroresDelServidor, traducirError } from '../../core/errores';
import { Cliente, Cuenta, Moneda, TipoCuenta } from '../../core/modelos';
import { importeValidator } from '../../core/validadores';
import { AvisoError } from '../../compartido/aviso-error';

/** Todas las cuentas, con alta de cuenta, depósito por ventanilla y bloqueo (sólo OPERADOR). */
@Component({
  selector: 'app-cuentas-operador',
  imports: [CurrencyPipe, ReactiveFormsModule, RouterLink, AvisoError],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1>Cuentas</h1>
    @if (aviso(); as a) {
      <p class="alerta exito" role="status">{{ a }}</p>
    }
    <app-aviso-error [error]="error()" />

    <section class="tarjeta">
      <table>
        <thead>
          <tr>
            <th scope="col">#</th>
            <th scope="col">Titular</th>
            <th scope="col">Tipo</th>
            <th scope="col">CBU</th>
            <th scope="col" class="num">Saldo</th>
            <th scope="col">Estado</th>
            <th scope="col">Acciones</th>
          </tr>
        </thead>
        <tbody>
          @for (c of cuentas(); track c.id) {
            <tr [class.bloqueada]="c.estado === 'BLOQUEADA'">
              <td>{{ c.id }}</td>
              <td>{{ c.titular }}</td>
              <td>{{ c.tipo }} {{ c.moneda }}</td>
              <td class="mono">{{ c.cbu }}</td>
              <td class="num">{{ c.saldo | currency: c.moneda : 'symbol-narrow' }}</td>
              <td>{{ c.estado === 'ACTIVA' ? 'Activa' : 'Bloqueada' }}</td>
              <td class="acciones-fila">
                <button type="button" class="secundario" (click)="elegirParaDeposito(c)">
                  Depositar
                </button>
                <button
                  type="button"
                  [class.peligro]="c.estado === 'ACTIVA'"
                  class="secundario"
                  (click)="cambiarEstado(c)"
                >
                  {{ c.estado === 'ACTIVA' ? 'Bloquear' : 'Desbloquear' }}
                </button>
                <a [routerLink]="['/cuentas', c.id, 'movimientos']">Movimientos</a>
              </td>
            </tr>
          }
        </tbody>
      </table>
    </section>

    <div class="dos-columnas">
      <form class="tarjeta" [formGroup]="deposito" (ngSubmit)="depositar()" novalidate>
        <h2>Depósito por ventanilla</h2>
        <label for="dep-cuenta">Cuenta</label>
        <select id="dep-cuenta" formControlName="cuentaId">
          <option [ngValue]="null" disabled>Elegí una cuenta</option>
          @for (c of cuentas(); track c.id) {
            <option [ngValue]="c.id">
              #{{ c.id }} · {{ c.titular }} · {{ c.tipo }} {{ c.moneda }}
            </option>
          }
        </select>
        <label for="dep-importe">Importe</label>
        <input id="dep-importe" type="number" step="0.01" formControlName="importe" />
        @if (deposito.controls.importe.touched && deposito.controls.importe.invalid) {
          <small class="campo-error">{{
            deposito.controls.importe.errors?.['servidor'] ??
              'Ingresá un importe mayor a cero, con hasta 2 decimales.'
          }}</small>
        }
        <label for="dep-desc">Descripción (opcional)</label>
        <input id="dep-desc" formControlName="descripcion" maxlength="120" />
        <button type="submit" [disabled]="enviando()">Depositar</button>
      </form>

      <form class="tarjeta" [formGroup]="alta" (ngSubmit)="crearCuenta()" novalidate>
        <h2>Abrir cuenta</h2>
        <label for="alta-cliente">Cliente</label>
        <select id="alta-cliente" formControlName="clienteId">
          <option [ngValue]="null" disabled>Elegí un cliente</option>
          @for (c of clientes(); track c.id) {
            <option [ngValue]="c.id">#{{ c.id }} · {{ c.apellido }}, {{ c.nombre }}</option>
          }
        </select>
        <div class="fila">
          <div>
            <label for="alta-tipo">Tipo</label>
            <select id="alta-tipo" formControlName="tipo">
              <option value="CA">Caja de ahorro</option>
              <option value="CC">Cuenta corriente</option>
            </select>
          </div>
          <div>
            <label for="alta-moneda">Moneda</label>
            <select id="alta-moneda" formControlName="moneda">
              <option value="ARS">ARS</option>
              <option value="USD">USD</option>
            </select>
          </div>
        </div>
        <label for="alta-limite">Límite diario de transferencias</label>
        <input id="alta-limite" type="number" step="0.01" formControlName="limiteDiario" />
        @if (alta.controls.limiteDiario.touched && alta.controls.limiteDiario.invalid) {
          <small class="campo-error">{{
            alta.controls.limiteDiario.errors?.['servidor'] ?? 'Ingresá un límite mayor a cero.'
          }}</small>
        }
        @if (alta.controls.tipo.value === 'CC') {
          <label for="alta-desc">Descubierto autorizado</label>
          <input id="alta-desc" type="number" step="0.01" formControlName="descubiertoAutorizado" />
        }
        <button type="submit" [disabled]="enviando()">Abrir cuenta</button>
      </form>
    </div>
  `,
})
export class CuentasOperador implements OnInit {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);

  readonly cuentas = signal<Cuenta[]>([]);
  readonly clientes = signal<Cliente[]>([]);
  readonly enviando = signal(false);
  readonly error = signal<ErrorApi | null>(null);
  readonly aviso = signal<string | null>(null);

  readonly deposito = this.fb.group({
    cuentaId: [null as number | null, Validators.required],
    importe: [null as number | null, [Validators.required, importeValidator]],
    descripcion: ['', Validators.maxLength(120)],
  });

  readonly alta = this.fb.group({
    clienteId: [null as number | null, Validators.required],
    tipo: ['CA' as TipoCuenta, Validators.required],
    moneda: ['ARS' as Moneda, Validators.required],
    limiteDiario: [100000 as number | null, [Validators.required, importeValidator]],
    descubiertoAutorizado: [null as number | null],
  });

  ngOnInit(): void {
    this.cargar();
    this.api.clientes().subscribe({
      next: (c) => this.clientes.set(c),
      error: (e: unknown) => this.error.set(traducirError(e)),
    });
  }

  cargar(): void {
    this.api.cuentas().subscribe({
      next: (c) => this.cuentas.set(c),
      error: (e: unknown) => this.error.set(traducirError(e)),
    });
  }

  elegirParaDeposito(c: Cuenta): void {
    this.deposito.controls.cuentaId.setValue(c.id);
    document.getElementById('dep-importe')?.focus();
  }

  depositar(): void {
    const v = this.deposito.getRawValue();
    if (this.deposito.invalid || v.cuentaId === null || v.importe === null) {
      this.deposito.markAllAsTouched();
      return;
    }
    this.iniciar();
    this.api.depositar(v.cuentaId, Number(v.importe), v.descripcion?.trim() || null).subscribe({
      next: (m) => {
        this.terminar(
          `Depósito registrado. Nuevo saldo de la cuenta #${v.cuentaId}: ${m.saldoPosterior}.`,
        );
        this.deposito.reset({ cuentaId: null, importe: null, descripcion: '' });
      },
      error: (e: unknown) => this.fallo(e, this.deposito),
    });
  }

  crearCuenta(): void {
    const v = this.alta.getRawValue();
    if (
      this.alta.invalid ||
      v.clienteId === null ||
      v.tipo === null ||
      v.moneda === null ||
      v.limiteDiario === null
    ) {
      this.alta.markAllAsTouched();
      return;
    }
    this.iniciar();
    this.api
      .crearCuenta({
        clienteId: v.clienteId,
        tipo: v.tipo,
        moneda: v.moneda,
        limiteDiario: Number(v.limiteDiario),
        descubiertoAutorizado:
          v.tipo === 'CC' && v.descubiertoAutorizado ? Number(v.descubiertoAutorizado) : null,
      })
      .subscribe({
        next: (c) => this.terminar(`Cuenta #${c.id} abierta. CBU ${c.cbu}.`),
        error: (e: unknown) => this.fallo(e, this.alta),
      });
  }

  cambiarEstado(c: Cuenta): void {
    const nuevo = c.estado === 'ACTIVA' ? 'BLOQUEADA' : 'ACTIVA';
    const accion = nuevo === 'BLOQUEADA' ? 'bloquear' : 'desbloquear';
    if (!window.confirm(`¿Seguro que querés ${accion} la cuenta #${c.id} de ${c.titular}?`)) {
      return;
    }
    this.iniciar();
    this.api.cambiarEstado(c.id, nuevo).subscribe({
      next: () =>
        this.terminar(`Cuenta #${c.id} ${nuevo === 'BLOQUEADA' ? 'bloqueada' : 'desbloqueada'}.`),
      error: (e: unknown) => this.fallo(e),
    });
  }

  private iniciar(): void {
    this.enviando.set(true);
    this.error.set(null);
    this.aviso.set(null);
  }

  private terminar(mensaje: string): void {
    this.enviando.set(false);
    this.aviso.set(mensaje);
    this.cargar();
  }

  private fallo(e: unknown, form?: typeof this.deposito | typeof this.alta): void {
    const error = traducirError(e);
    if (form) {
      marcarErroresDelServidor(form, error);
    }
    this.error.set(error);
    this.enviando.set(false);
  }
}
