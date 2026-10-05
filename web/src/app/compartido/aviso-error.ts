import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { ErrorApi } from '../core/errores';

/** Caja de error accesible (role="alert") con el mensaje ya traducido. */
@Component({
  selector: 'app-aviso-error',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (error(); as e) {
      <div class="alerta error" role="alert">
        <p>{{ e.mensaje }}</p>
        @if (e.codigo) {
          <small>Código: {{ e.codigo }}</small>
        }
      </div>
    }
  `,
})
export class AvisoError {
  readonly error = input<ErrorApi | null>(null);
}
