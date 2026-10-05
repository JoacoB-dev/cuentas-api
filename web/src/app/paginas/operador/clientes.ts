import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { ErrorApi, marcarErroresDelServidor, traducirError } from '../../core/errores';
import { Cliente } from '../../core/modelos';
import { AvisoError } from '../../compartido/aviso-error';

/** Alta y listado de clientes (sólo OPERADOR). */
@Component({
  selector: 'app-clientes',
  imports: [ReactiveFormsModule, AvisoError],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1>Clientes</h1>
    <div class="dos-columnas">
      <form class="tarjeta" [formGroup]="form" (ngSubmit)="crear()" novalidate>
        <h2>Alta de cliente</h2>
        <label for="nombre">Nombre</label>
        <input id="nombre" formControlName="nombre" maxlength="80" />
        @if (errorDe('nombre'); as m) {
          <small class="campo-error">{{ m }}</small>
        }

        <label for="apellido">Apellido</label>
        <input id="apellido" formControlName="apellido" maxlength="80" />
        @if (errorDe('apellido'); as m) {
          <small class="campo-error">{{ m }}</small>
        }

        <label for="dni">DNI (sin puntos)</label>
        <input id="dni" formControlName="dni" inputmode="numeric" maxlength="8" />
        @if (errorDe('dni'); as m) {
          <small class="campo-error">{{ m }}</small>
        }

        <label for="email">Email</label>
        <input id="email" type="email" formControlName="email" />
        @if (errorDe('email'); as m) {
          <small class="campo-error">{{ m }}</small>
        }

        <app-aviso-error [error]="error()" />
        @if (creado(); as c) {
          <p class="alerta exito" role="status">
            Cliente #{{ c.id }} {{ c.nombre }} {{ c.apellido }} creado.
          </p>
        }
        <button type="submit" [disabled]="enviando()">Crear cliente</button>
      </form>

      <section class="tarjeta">
        <h2>Listado</h2>
        <table>
          <thead>
            <tr>
              <th scope="col">#</th>
              <th scope="col">Nombre</th>
              <th scope="col">DNI</th>
              <th scope="col">Email</th>
            </tr>
          </thead>
          <tbody>
            @for (c of clientes(); track c.id) {
              <tr>
                <td>{{ c.id }}</td>
                <td>{{ c.apellido }}, {{ c.nombre }}</td>
                <td>{{ c.dni }}</td>
                <td>{{ c.email }}</td>
              </tr>
            }
          </tbody>
        </table>
      </section>
    </div>
  `,
})
export class Clientes implements OnInit {
  private readonly api = inject(ApiService);

  readonly clientes = signal<Cliente[]>([]);
  readonly enviando = signal(false);
  readonly error = signal<ErrorApi | null>(null);
  readonly creado = signal<Cliente | null>(null);

  readonly form = inject(FormBuilder).nonNullable.group({
    nombre: ['', [Validators.required, Validators.maxLength(80)]],
    apellido: ['', [Validators.required, Validators.maxLength(80)]],
    dni: ['', [Validators.required, Validators.pattern(/^\d{7,8}$/)]],
    email: ['', [Validators.required, Validators.email]],
  });

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.api.clientes().subscribe({
      next: (c) => this.clientes.set(c),
      error: (e: unknown) => this.error.set(traducirError(e)),
    });
  }

  crear(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.enviando.set(true);
    this.error.set(null);
    this.creado.set(null);
    this.api.crearCliente(this.form.getRawValue()).subscribe({
      next: (c) => {
        this.creado.set(c);
        this.form.reset();
        this.enviando.set(false);
        this.cargar();
      },
      error: (e: unknown) => {
        const error = traducirError(e);
        marcarErroresDelServidor(this.form, error);
        this.error.set(error);
        this.enviando.set(false);
      },
    });
  }

  /** Mensaje de error de un campo (o null si está bien o no se tocó). */
  errorDe(campo: keyof typeof this.form.controls): string | null {
    const c = this.form.controls[campo];
    if (!c.touched || !c.errors) {
      return null;
    }
    if (c.errors['servidor']) {
      return String(c.errors['servidor']);
    }
    if (c.errors['required']) {
      return 'Este dato es obligatorio.';
    }
    if (c.errors['pattern']) {
      return 'El DNI debe tener 7 u 8 dígitos, sin puntos.';
    }
    if (c.errors['email']) {
      return 'El email no tiene un formato válido.';
    }
    return 'Dato inválido.';
  }
}
