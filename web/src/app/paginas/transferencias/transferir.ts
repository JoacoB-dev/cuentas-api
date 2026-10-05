import { CurrencyPipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { ErrorApi, marcarErroresDelServidor, traducirError } from '../../core/errores';
import { generarClaveIdempotencia } from '../../core/idempotencia';
import { Cuenta, Transferencia } from '../../core/modelos';
import { cbuValidator, importeValidator } from '../../core/validadores';
import { AvisoError } from '../../compartido/aviso-error';

type Paso = 'formulario' | 'confirmacion' | 'hecha';

/**
 * Transferencia en tres pasos: datos → confirmación → comprobante.
 *
 * La Idempotency-Key se genera al pasar a la confirmación y se reusa en cada
 * reintento de ESA transferencia. Si la conexión se corta y el usuario vuelve a
 * tocar "Confirmar", la API reconoce la clave y devuelve la transferencia original
 * en vez de hacer otra. Si el usuario vuelve a editar los datos, se genera una nueva.
 */
@Component({
  selector: 'app-transferir',
  imports: [CurrencyPipe, ReactiveFormsModule, RouterLink, AvisoError],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1>Transferir</h1>
    @switch (paso()) {
      @case ('formulario') {
        <form class="tarjeta angosta" [formGroup]="form" (ngSubmit)="continuar()" novalidate>
          <label for="origen">Cuenta de origen</label>
          <select id="origen" formControlName="cuentaOrigenId">
            <option [ngValue]="null" disabled>Elegí una cuenta</option>
            @for (c of cuentasActivas(); track c.id) {
              <option [ngValue]="c.id">
                {{ c.tipo }} {{ c.moneda }} · {{ c.cbu.slice(-6) }} ·
                {{ c.disponible | currency: c.moneda : 'symbol-narrow' }}
              </option>
            }
          </select>
          @if (mostrarError('cuentaOrigenId')) {
            <small class="campo-error">{{ mensajeCampo('cuentaOrigenId') }}</small>
          }

          <label for="cbu">CBU de destino</label>
          <input
            id="cbu"
            formControlName="cbuDestino"
            inputmode="numeric"
            maxlength="22"
            placeholder="22 dígitos"
          />
          @if (mostrarError('cbuDestino')) {
            <small class="campo-error">{{ mensajeCampo('cbuDestino') }}</small>
          }

          <label for="importe">Importe</label>
          <input id="importe" type="number" step="0.01" min="0.01" formControlName="importe" />
          @if (mostrarError('importe')) {
            <small class="campo-error">{{ mensajeCampo('importe') }}</small>
          }

          <label for="concepto">Concepto (opcional)</label>
          <input id="concepto" formControlName="concepto" maxlength="140" />
          @if (mostrarError('concepto')) {
            <small class="campo-error">{{ mensajeCampo('concepto') }}</small>
          }

          <app-aviso-error [error]="error()" />
          <button type="submit">Continuar</button>
        </form>
      }
      @case ('confirmacion') {
        <section class="tarjeta angosta" aria-labelledby="titulo-confirmar">
          <h2 id="titulo-confirmar">Revisá los datos</h2>
          <dl class="confirmacion">
            <dt>Desde</dt>
            <dd>
              {{ cuentaOrigen()?.tipo }} {{ cuentaOrigen()?.moneda }} ·
              <span class="mono">{{ cuentaOrigen()?.cbu }}</span>
            </dd>
            <dt>Hacia CBU</dt>
            <dd class="mono">{{ form.controls.cbuDestino.value }}</dd>
            <dt>Importe</dt>
            <dd class="importe-grande">
              {{
                form.controls.importe.value
                  | currency: cuentaOrigen()?.moneda ?? 'ARS' : 'symbol-narrow'
              }}
            </dd>
            @if (form.controls.concepto.value) {
              <dt>Concepto</dt>
              <dd>{{ form.controls.concepto.value }}</dd>
            }
          </dl>
          <app-aviso-error [error]="error()" />
          @if (resultadoIncierto()) {
            <p class="alerta info" role="status">
              No sabemos si la transferencia llegó a hacerse. Podés tocar "Confirmar" de nuevo sin
              miedo: se reenvía con la misma clave y, si ya se había hecho, no se repite.
            </p>
          }
          <div class="acciones">
            <button type="button" class="secundario" (click)="modificar()" [disabled]="enviando()">
              Modificar
            </button>
            <button type="button" (click)="confirmar()" [disabled]="enviando()">
              {{ enviando() ? 'Enviando…' : 'Confirmar transferencia' }}
            </button>
          </div>
          <small class="clave"
            >Clave de idempotencia: <span class="mono">{{ clave() }}</span></small
          >
        </section>
      }
      @case ('hecha') {
        @if (resultado(); as t) {
          <section class="tarjeta angosta exito" role="status">
            <h2>Transferencia realizada</h2>
            @if (repetida()) {
              <p class="alerta info">
                Esta transferencia ya se había hecho antes; no se volvió a debitar.
              </p>
            }
            <dl>
              <dt>Número</dt>
              <dd>#{{ t.id }}</dd>
              <dt>Importe</dt>
              <dd>{{ t.importe | currency: t.moneda : 'symbol-narrow' }}</dd>
            </dl>
            <div class="acciones">
              <a [routerLink]="['/cuentas', t.cuentaOrigenId, 'movimientos']">Ver movimientos</a>
              <button type="button" class="secundario" (click)="nueva()">Hacer otra</button>
            </div>
          </section>
        }
      }
    }
  `,
})
export class Transferir implements OnInit {
  private readonly api = inject(ApiService);

  /** ?origen=<id> para llegar con la cuenta ya elegida desde "Mis cuentas". */
  readonly origen = input<string>();

  readonly cuentas = signal<Cuenta[]>([]);
  readonly cuentasActivas = computed(() => this.cuentas().filter((c) => c.estado === 'ACTIVA'));
  readonly paso = signal<Paso>('formulario');
  readonly clave = signal<string | null>(null);
  readonly enviando = signal(false);
  readonly error = signal<ErrorApi | null>(null);
  readonly resultadoIncierto = signal(false);
  readonly resultado = signal<Transferencia | null>(null);
  readonly repetida = signal(false);

  readonly form = inject(FormBuilder).group({
    cuentaOrigenId: [null as number | null, Validators.required],
    cbuDestino: ['', [Validators.required, cbuValidator]],
    importe: [null as number | null, [Validators.required, importeValidator]],
    concepto: ['', Validators.maxLength(140)],
  });

  /** Cuenta de origen elegida, para mostrar en la confirmación. */
  readonly cuentaOrigen = signal<Cuenta | null>(null);

  ngOnInit(): void {
    this.api.cuentas().subscribe({
      next: (c) => {
        this.cuentas.set(c);
        const elegida = Number(this.origen());
        if (elegida && c.some((x) => x.id === elegida && x.estado === 'ACTIVA')) {
          this.form.controls.cuentaOrigenId.setValue(elegida);
        }
      },
      error: (e: unknown) => this.error.set(traducirError(e)),
    });
  }

  continuar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.cuentaOrigen.set(
      this.cuentas().find((c) => c.id === this.form.controls.cuentaOrigenId.value) ?? null,
    );
    // Una clave nueva por cada transferencia que el usuario arma.
    this.clave.set(generarClaveIdempotencia());
    this.error.set(null);
    this.resultadoIncierto.set(false);
    this.paso.set('confirmacion');
  }

  confirmar(): void {
    const clave = this.clave();
    const v = this.form.getRawValue();
    if (!clave || v.cuentaOrigenId === null || v.importe === null) {
      return;
    }
    this.enviando.set(true);
    this.error.set(null);
    const concepto = v.concepto?.trim();
    this.api
      .transferir(
        {
          cuentaOrigenId: v.cuentaOrigenId,
          cbuDestino: (v.cbuDestino ?? '').trim(),
          importe: Number(v.importe),
          ...(concepto ? { concepto } : {}),
        },
        clave,
      )
      .subscribe({
        next: (respuesta) => {
          this.resultado.set(respuesta.body);
          this.repetida.set(respuesta.headers.get('Idempotent-Replayed') === 'true');
          this.enviando.set(false);
          this.paso.set('hecha');
        },
        error: (e: unknown) => {
          const error = traducirError(e);
          this.enviando.set(false);
          this.error.set(error);
          // Sin respuesta o error del servidor: no sabemos si se hizo. Se ofrece reintentar con la misma clave.
          this.resultadoIncierto.set(error.status === 0 || error.status >= 500);
          if (error.status === 400 && Object.keys(error.campos).length > 0) {
            marcarErroresDelServidor(this.form, error);
            this.paso.set('formulario');
          }
        },
      });
  }

  /** Volver a editar: la próxima confirmación va a usar otra clave. */
  modificar(): void {
    this.clave.set(null);
    this.paso.set('formulario');
  }

  nueva(): void {
    this.form.reset({ cuentaOrigenId: null, cbuDestino: '', importe: null, concepto: '' });
    this.clave.set(null);
    this.resultado.set(null);
    this.repetida.set(false);
    this.error.set(null);
    this.paso.set('formulario');
  }

  mostrarError(campo: keyof typeof this.form.controls): boolean {
    const c = this.form.controls[campo];
    return c.invalid && c.touched;
  }

  mensajeCampo(campo: keyof typeof this.form.controls): string {
    const errores = this.form.controls[campo].errors ?? {};
    if (errores['servidor']) {
      return String(errores['servidor']);
    }
    if (errores['required']) {
      return 'Este dato es obligatorio.';
    }
    if (errores['cbu']) {
      return 'El CBU tiene que tener 22 dígitos y dígitos verificadores válidos.';
    }
    if (errores['importe']) {
      return 'Ingresá un importe mayor a cero, con hasta 2 decimales.';
    }
    if (errores['maxlength']) {
      return 'El concepto admite hasta 140 caracteres.';
    }
    return 'Dato inválido.';
  }
}
