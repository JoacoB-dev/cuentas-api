import { HttpErrorResponse } from '@angular/common/http';
import { AbstractControl, FormGroup } from '@angular/forms';
import { ProblemDetail } from './modelos';

/** Error ya traducido para mostrar en pantalla. */
export interface ErrorApi {
  /** Mensaje general, en castellano. */
  mensaje: string;
  /** Código estable de la API (p. ej. "saldo-insuficiente"), si lo hay. */
  codigo: string | null;
  status: number;
  /** Errores de validación por campo (400 con lista "errores"). */
  campos: Record<string, string>;
}

/**
 * Convierte cualquier error de HttpClient en un mensaje en castellano.
 * La API ya responde ProblemDetail con "detail" en castellano: si está, se usa tal cual.
 * Si no (la API está caída, un proxy devolvió HTML, etc.), se arma un mensaje por status.
 */
export function traducirError(error: unknown): ErrorApi {
  if (!(error instanceof HttpErrorResponse)) {
    return { mensaje: 'Ocurrió un error inesperado.', codigo: null, status: -1, campos: {} };
  }
  const problema = esProblemDetail(error.error) ? error.error : null;
  const campos: Record<string, string> = {};
  for (const e of problema?.errores ?? []) {
    campos[e.campo] = e.mensaje;
  }
  return {
    mensaje: problema?.detail ?? mensajePorStatus(error.status),
    codigo: problema?.codigo ?? null,
    status: error.status,
    campos,
  };
}

export function mensajePorStatus(status: number): string {
  switch (status) {
    case 0:
      return 'No se pudo conectar con el servidor. Revisá tu conexión e intentá de nuevo.';
    case 400:
      return 'Hay datos inválidos en el pedido.';
    case 401:
      return 'Tu sesión venció. Volvé a iniciar sesión.';
    case 403:
      return 'No tenés permiso para hacer esta operación.';
    case 404:
      return 'No encontramos lo que buscabas.';
    case 409:
      return 'La operación entra en conflicto con datos existentes.';
    case 422:
      return 'La operación fue rechazada.';
    case 429:
      return 'Demasiados intentos. Esperá un rato y probá de nuevo.';
    case 502:
    case 503:
    case 504:
      return 'El servicio no está disponible en este momento. Probá de nuevo en unos minutos.';
    default:
      return status >= 500
        ? 'El servidor tuvo un problema. Si se repite, avisá a soporte.'
        : 'No se pudo completar la operación.';
  }
}

function esProblemDetail(cuerpo: unknown): cuerpo is ProblemDetail {
  return (
    typeof cuerpo === 'object' && cuerpo !== null && ('detail' in cuerpo || 'codigo' in cuerpo)
  );
}

/**
 * Pega los errores de validación de la API en los controles del formulario
 * (error "servidor"), así se muestran debajo de cada campo.
 */
export function marcarErroresDelServidor(form: FormGroup, error: ErrorApi): void {
  for (const [campo, mensaje] of Object.entries(error.campos)) {
    const control: AbstractControl | null = form.get(campo);
    if (control) {
      control.setErrors({ ...(control.errors ?? {}), servidor: mensaje });
      control.markAsTouched();
    }
  }
}
