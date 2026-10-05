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
import { Extracto } from '../../core/modelos';
import { rangoDeFechasValidator } from '../../core/validadores';
import { AvisoError } from '../../compartido/aviso-error';
import { hoyEnArgentina, restarDias } from '../../compartido/formato';

@Component({
  selector: 'app-extracto',
  imports: [CurrencyPipe, DatePipe, ReactiveFormsModule, RouterLink, AvisoError],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a routerLink="/inicio" class="volver no-imprimir">← Volver</a>
    <h1>Extracto</h1>
    <form class="filtros no-imprimir" [formGroup]="filtro" (ngSubmit)="consultar()" novalidate>
      <div>
        <label for="ext-desde">Desde</label>
        <input id="ext-desde" type="date" formControlName="desde" />
      </div>
      <div>
        <label for="ext-hasta">Hasta</label>
        <input id="ext-hasta" type="date" formControlName="hasta" />
      </div>
      <button type="submit" [disabled]="filtro.invalid">Consultar</button>
      <button type="button" class="secundario" (click)="imprimir()" [disabled]="!extracto()">
        Imprimir
      </button>
      @if (filtro.hasError('rango')) {
        <small class="campo-error">"Desde" no puede ser posterior a "Hasta".</small>
      }
    </form>
    <app-aviso-error [error]="error()" />

    @if (extracto(); as x) {
      <section class="tarjeta">
        <p class="subtitulo">
          {{ x.titular }} · CBU <span class="mono">{{ x.cbu }}</span> · del
          {{ x.desde | date: 'dd/MM/yyyy' : 'UTC' }} al {{ x.hasta | date: 'dd/MM/yyyy' : 'UTC' }}
        </p>
        <div class="resumen">
          <div>
            <span>Saldo inicial</span
            ><strong>{{ x.saldoInicial | currency: x.moneda : 'symbol-narrow' }}</strong>
          </div>
          <div>
            <span>Créditos</span
            ><strong class="credito"
              >+{{ x.totalCreditos | currency: x.moneda : 'symbol-narrow' }}</strong
            >
          </div>
          <div>
            <span>Débitos</span
            ><strong class="debito"
              >−{{ x.totalDebitos | currency: x.moneda : 'symbol-narrow' }}</strong
            >
          </div>
          <div>
            <span>Saldo final</span
            ><strong>{{ x.saldoFinal | currency: x.moneda : 'symbol-narrow' }}</strong>
          </div>
        </div>
        <table>
          <caption class="sr-only">
            Movimientos del extracto
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
            @for (m of x.movimientos; track m.id) {
              <tr>
                <td>{{ m.fecha | date: 'dd/MM/yyyy HH:mm' : '-0300' }}</td>
                <td>{{ m.descripcion }}</td>
                <td
                  class="num"
                  [class.credito]="m.tipo === 'CREDITO'"
                  [class.debito]="m.tipo === 'DEBITO'"
                >
                  {{ m.tipo === 'DEBITO' ? '−' : '+'
                  }}{{ m.importe | currency: x.moneda : 'symbol-narrow' }}
                </td>
                <td class="num">{{ m.saldoPosterior | currency: x.moneda : 'symbol-narrow' }}</td>
              </tr>
            } @empty {
              <tr>
                <td colspan="4">No hay movimientos en el período.</td>
              </tr>
            }
          </tbody>
        </table>
      </section>
    }
  `,
})
export class ExtractoCuenta implements OnInit {
  private readonly api = inject(ApiService);

  readonly id = input.required({ transform: numberAttribute });
  readonly extracto = signal<Extracto | null>(null);
  readonly error = signal<ErrorApi | null>(null);

  readonly filtro = inject(FormBuilder).nonNullable.group(
    { desde: restarDias(hoyEnArgentina(), 30), hasta: hoyEnArgentina() },
    { validators: rangoDeFechasValidator },
  );

  ngOnInit(): void {
    this.consultar();
  }

  consultar(): void {
    if (this.filtro.invalid) {
      return;
    }
    this.error.set(null);
    this.api.extracto(this.id(), this.filtro.getRawValue()).subscribe({
      next: (x) => this.extracto.set(x),
      error: (e: unknown) => {
        this.extracto.set(null);
        this.error.set(traducirError(e));
      },
    });
  }

  imprimir(): void {
    window.print();
  }
}
