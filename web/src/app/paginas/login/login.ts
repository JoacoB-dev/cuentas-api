import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiService } from '../../core/api.service';
import { ErrorApi, traducirError } from '../../core/errores';
import { SesionService } from '../../core/sesion.service';
import { AvisoError } from '../../compartido/aviso-error';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, AvisoError],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="tarjeta angosta">
      <h1>Ingresar</h1>
      @if (motivo() === 'sesion-vencida') {
        <div class="alerta info" role="status">Tu sesión venció. Volvé a ingresar.</div>
      }
      <form [formGroup]="form" (ngSubmit)="ingresar()" novalidate>
        <label for="usuario">Usuario</label>
        <input id="usuario" formControlName="username" autocomplete="username" />
        @if (form.controls.username.touched && form.controls.username.hasError('required')) {
          <small class="campo-error">Ingresá tu usuario.</small>
        }

        <label for="password">Contraseña</label>
        <input
          id="password"
          type="password"
          formControlName="password"
          autocomplete="current-password"
        />
        @if (form.controls.password.touched && form.controls.password.hasError('required')) {
          <small class="campo-error">Ingresá tu contraseña.</small>
        }

        <app-aviso-error [error]="error()" />
        <button type="submit" [disabled]="enviando()">
          {{ enviando() ? 'Ingresando…' : 'Ingresar' }}
        </button>
      </form>
      <details class="ayuda">
        <summary>Usuarios de prueba</summary>
        <ul>
          <li><code>ana / ana123</code> y <code>bruno / bruno123</code> (cliente)</li>
          <li><code>operador / operador123</code> (operador)</li>
        </ul>
      </details>
    </section>
  `,
})
export class Login {
  private readonly api = inject(ApiService);
  private readonly sesion = inject(SesionService);
  private readonly router = inject(Router);

  /** Query params ?motivo= y ?volver= (los inyecta el router con withComponentInputBinding). */
  readonly motivo = input<string>();
  readonly volver = input<string>();

  readonly form = inject(FormBuilder).nonNullable.group({
    username: ['', Validators.required],
    password: ['', Validators.required],
  });
  readonly enviando = signal(false);
  readonly error = signal<ErrorApi | null>(null);

  ingresar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { username, password } = this.form.getRawValue();
    this.enviando.set(true);
    this.error.set(null);
    this.api.login(username.trim(), password).subscribe({
      next: (respuesta) => {
        this.sesion.iniciar(username.trim(), respuesta);
        // Sólo se vuelve a rutas internas (evita redirecciones abiertas).
        const destino =
          this.volver()?.startsWith('/') && !this.volver()?.startsWith('//')
            ? this.volver()!
            : '/inicio';
        void this.router.navigateByUrl(destino);
      },
      error: (e: unknown) => {
        this.error.set(traducirError(e));
        this.enviando.set(false);
      },
    });
  }
}
