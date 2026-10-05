import { CurrencyPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { ErrorApi, traducirError } from '../../core/errores';
import { Cuenta } from '../../core/modelos';
import { AvisoError } from '../../compartido/aviso-error';
import { NOMBRE_TIPO_CUENTA } from '../../compartido/formato';

@Component({
  selector: 'app-mis-cuentas',
  imports: [CurrencyPipe, RouterLink, AvisoError],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1>Mis cuentas</h1>
    <app-aviso-error [error]="error()" />
    @if (cargando()) {
      <p class="cargando">Cargando cuentas…</p>
    } @else {
      <div class="grilla-cuentas">
        @for (c of cuentas(); track c.id) {
          <article class="tarjeta cuenta" [class.bloqueada]="c.estado === 'BLOQUEADA'">
            <header>
              <h2>{{ tipos[c.tipo] }} en {{ c.moneda }}</h2>
              @if (c.estado === 'BLOQUEADA') {
                <span class="etiqueta peligro">Bloqueada</span>
              }
            </header>
            <p class="saldo" data-testid="saldo">
              {{ c.saldo | currency: c.moneda : 'symbol-narrow' }}
            </p>
            <dl>
              <dt>Disponible</dt>
              <dd>{{ c.disponible | currency: c.moneda : 'symbol-narrow' }}</dd>
              <dt>CBU</dt>
              <dd class="mono">{{ c.cbu }}</dd>
              <dt>Límite diario</dt>
              <dd>{{ c.limiteDiario | currency: c.moneda : 'symbol-narrow' }}</dd>
            </dl>
            <footer>
              <a [routerLink]="['/cuentas', c.id, 'movimientos']">Movimientos</a>
              <a [routerLink]="['/cuentas', c.id, 'extracto']">Extracto</a>
              @if (c.estado === 'ACTIVA') {
                <a routerLink="/transferir" [queryParams]="{ origen: c.id }">Transferir</a>
              }
            </footer>
          </article>
        } @empty {
          <p>No tenés cuentas.</p>
        }
      </div>
    }
  `,
})
export class MisCuentas implements OnInit {
  private readonly api = inject(ApiService);
  protected readonly tipos = NOMBRE_TIPO_CUENTA;

  readonly cuentas = signal<Cuenta[]>([]);
  readonly cargando = signal(true);
  readonly error = signal<ErrorApi | null>(null);

  ngOnInit(): void {
    this.api.cuentas().subscribe({
      next: (c) => {
        this.cuentas.set(c);
        this.cargando.set(false);
      },
      error: (e: unknown) => {
        this.error.set(traducirError(e));
        this.cargando.set(false);
      },
    });
  }
}
