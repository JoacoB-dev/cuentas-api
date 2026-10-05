import { Injectable, computed, signal } from '@angular/core';
import { LoginResponse, Rol } from './modelos';

/** Lo que se guarda de la sesión. El token es un JWT firmado por la API. */
export interface DatosSesion {
  token: string;
  rol: Rol;
  usuario: string;
  venceEn: string;
}

const CLAVE_STORAGE = 'cuentas-web.sesion';

/**
 * Estado de la sesión con signals. Se guarda en sessionStorage (se borra al cerrar la
 * pestaña) y no en localStorage, para que el token no quede en el navegador para siempre.
 * Igual, cualquier script que corra en la página podría leerlo: por eso importa que la
 * app no inserte HTML de terceros (Angular escapa todo por defecto).
 */
@Injectable({ providedIn: 'root' })
export class SesionService {
  private readonly datos = signal<DatosSesion | null>(leerGuardada());

  readonly sesion = this.datos.asReadonly();
  readonly token = computed(() => this.datos()?.token ?? null);
  readonly rol = computed(() => this.datos()?.rol ?? null);
  readonly usuario = computed(() => this.datos()?.usuario ?? null);
  readonly esOperador = computed(() => this.rol() === 'OPERADOR');

  /** true si hay token y todavía no venció (según la fecha que informó la API). */
  estaAutenticado(ahora: Date = new Date()): boolean {
    const s = this.datos();
    if (!s) {
      return false;
    }
    if (new Date(s.venceEn).getTime() <= ahora.getTime()) {
      this.cerrar();
      return false;
    }
    return true;
  }

  iniciar(usuario: string, respuesta: LoginResponse): void {
    const datos: DatosSesion = {
      token: respuesta.token,
      rol: respuesta.rol,
      usuario,
      venceEn: respuesta.venceEn,
    };
    this.datos.set(datos);
    try {
      sessionStorage.setItem(CLAVE_STORAGE, JSON.stringify(datos));
    } catch {
      // Navegación privada o storage bloqueado: la sesión vive sólo en memoria.
    }
  }

  cerrar(): void {
    this.datos.set(null);
    try {
      sessionStorage.removeItem(CLAVE_STORAGE);
    } catch {
      // nada que hacer
    }
  }
}

function leerGuardada(): DatosSesion | null {
  try {
    const crudo = sessionStorage.getItem(CLAVE_STORAGE);
    if (!crudo) {
      return null;
    }
    const datos = JSON.parse(crudo) as Partial<DatosSesion>;
    if (datos.token && datos.rol && datos.usuario && datos.venceEn) {
      return datos as DatosSesion;
    }
  } catch {
    // dato corrupto: se ignora
  }
  return null;
}
