import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { SesionService } from './core/sesion.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="barra">
      <a class="marca" routerLink="/inicio">Banco <strong>Demo</strong></a>
      @if (sesion.sesion(); as s) {
        <nav aria-label="Principal">
          @if (s.rol === 'CLIENTE') {
            <a routerLink="/cuentas" routerLinkActive="activo">Mis cuentas</a>
            <a routerLink="/transferir" routerLinkActive="activo">Transferir</a>
          } @else {
            <a routerLink="/operador/cuentas" routerLinkActive="activo">Cuentas</a>
            <a routerLink="/operador/clientes" routerLinkActive="activo">Clientes</a>
          }
        </nav>
        <div class="usuario">
          <span>{{ s.usuario }} · {{ s.rol === 'OPERADOR' ? 'Operador' : 'Cliente' }}</span>
          <button type="button" class="secundario" (click)="salir()">Salir</button>
        </div>
      }
    </header>
    <main>
      <router-outlet />
    </main>
    <footer>Proyecto de portfolio · datos de ejemplo · no es un banco real</footer>
  `,
})
export class App {
  protected readonly sesion = inject(SesionService);
  private readonly router = inject(Router);

  salir(): void {
    this.sesion.cerrar();
    void this.router.navigate(['/login']);
  }
}
