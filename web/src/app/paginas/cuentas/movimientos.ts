import { CurrencyPipe, DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  inject,
  input,
  numberAttribute,
  signal,
} from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { ErrorApi, traducirError } from '../../core/errores';
import { Cuenta, Movimiento, Pagina } from '../../core/modelos';
import { rangoDeFechasValidator } from '../../core/validadores';
import { AvisoError } from '../../compartido/aviso-error';
import { hoyEnArgentina, restarDias } from '../../compartido/formato';

export const TAMANIO_PAGINA = 10;

@Component({
  selector: 'app-movimientos',
  imports: [CurrencyPipe, DatePipe, ReactiveFormsModule, RouterLink, AvisoError],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a routerLink="/inicio" class="volver">← Volver</a>
    <h1>Movimientos</h1>
    @if (cuenta(); as c) {
      <p class="subtitulo">
        {{ c.titular }} · <span class="mono">{{ c.cbu }}</span> · {{ c.moneda }}
      </p>
    }

    <form class="filtros" [formGroup]="filtro" (ngSubmit)="buscar()" novalidate>
      <div>
        <label for="desde">Desde</label>
        <input id="desde" type="date" formControlName="desde" />
      </div>
      <div>
        <label for="hasta">Hasta</label>
        <input id="hasta" type="date" formControlName="hasta" />
      </div>
      <button type="submit" [disabled]="filtro.invalid">Filtrar</button>
      @if (filtro.hasError('rango')) {
        <small class="campo-error">"Desde" no puede ser posterior a "Hasta".</small>
      }
    </form>

    <app-aviso-error [error]="error()" />

    @if (pagina(); as p) {
      <table>
        <caption class="sr-only">
          Movimientos de la cuenta
        </caption>
        <thead>
          <tr>
            <th scope="col">Fecha</th>
            <th scope="col">Descripción</th>
            <th scope="col" class="num">Importe</th>
            <th scope="col" class="num">Saldo</th>
          </tr>
        </thead>
        <tbody>
          @for (m of p.contenido; track m.id) {
            <tr>
              <td>{{ m.fecha | date: 'dd/MM/yyyy HH:mm' : '-0300' }}</td>
              <td>{{ m.descripcion }}</td>
              <td
                class="num"
                [class.credito]="m.tipo === 'CREDITO'"
                [class.debito]="m.tipo === 'DEBITO'"
              >
                {{ m.tipo === 'DEBITO' ? '−' : '+'
                }}{{ m.importe | currency: moneda() : 'symbol-narrow' }}
              </td>
              <td class="num">{{ m.saldoPosterior | currency: moneda() : 'symbol-narrow' }}</td>
            </tr>
          } @empty {
            <tr>
              <td colspan="4">No hay movimientos en el período.</td>
            </tr>
          }
        </tbody>
      </table>
      <nav class="paginador" aria-label="Paginación">
        <button
          type="button"
          class="secundario"
          (click)="irA(p.pagina - 1)"
          [disabled]="p.pagina === 0 || cargando()"
        >
          Anterior
        </button>
        <span
          >Página {{ p.totalPaginas === 0 ? 0 : p.pagina + 1 }} de {{ p.totalPaginas }} ·
          {{ p.totalElementos }} movimientos</span
        >
        <button
          type="button"
          class="secundario"
          (click)="irA(p.pagina + 1)"
          [disabled]="p.pagina + 1 >= p.totalPaginas || cargando()"
        >
          Siguiente
        </button>
      </nav>
    } @else if (cargando()) {
      <p class="cargando">Cargando…</p>
    }
  `,
})
export class Movimientos implements OnInit {
  private readonly api = inject(ApiService);

  /** Parámetro :id de la ruta. */
  readonly id = input.required({ transform: numberAttribute });

  readonly cuenta = signal<Cuenta | null>(null);
  readonly moneda = signal<string>('ARS');
  readonly pagina = signal<Pagina<Movimiento> | null>(null);
  readonly cargando = signal(false);
  readonly error = signal<ErrorApi | null>(null);

  readonly filtro = inject(FormBuilder).nonNullable.group(
    {
      desde: restarDias(hoyEnArgentina(), 30),
      hasta: hoyEnArgentina(),
    },
    { validators: rangoDeFechasValidator },
  );

  ngOnInit(): void {
    this.api.cuenta(this.id()).subscribe({
      next: (c) => {
        this.cuenta.set(c);
        this.moneda.set(c.moneda);
      },
      error: (e: unknown) => this.error.set(traducirError(e)),
    });
    this.irA(0);
  }

  buscar(): void {
    if (this.filtro.valid) {
      this.irA(0);
    }
  }

  irA(numero: number): void {
    this.cargando.set(true);
    this.error.set(null);
    this.api.movimientos(this.id(), this.filtro.getRawValue(), numero, TAMANIO_PAGINA).subscribe({
      next: (p) => {
        this.pagina.set(p);
        this.cargando.set(false);
      },
      error: (e: unknown) => {
        this.error.set(traducirError(e));
        this.cargando.set(false);
      },
    });
  }
}
