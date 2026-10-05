import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  Cliente,
  ClienteRequest,
  Cuenta,
  CuentaRequest,
  EstadoCuenta,
  Extracto,
  LoginResponse,
  Movimiento,
  Pagina,
  Transferencia,
  TransferenciaRequest,
} from './modelos';

/** Filtro de fechas en formato AAAA-MM-DD (hora argentina, inclusivas). */
export interface FiltroFechas {
  desde?: string | null;
  hasta?: string | null;
}

/**
 * Acceso a la API REST. Las URLs son relativas (/api/...): en desarrollo las
 * redirige el proxy de `ng serve` y en Docker las redirige nginx. Así no hace falta CORS.
 */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  login(username: string, password: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>('/api/auth/login', { username, password });
  }

  cuentas(clienteId?: number): Observable<Cuenta[]> {
    const params = clienteId ? new HttpParams().set('clienteId', clienteId) : undefined;
    return this.http.get<Cuenta[]>('/api/cuentas', { params });
  }

  cuenta(id: number): Observable<Cuenta> {
    return this.http.get<Cuenta>(`/api/cuentas/${id}`);
  }

  movimientos(
    id: number,
    filtro: FiltroFechas,
    pagina: number,
    tamanio: number,
  ): Observable<Pagina<Movimiento>> {
    const params = conFechas(
      new HttpParams().set('pagina', pagina).set('tamanio', tamanio),
      filtro,
    );
    return this.http.get<Pagina<Movimiento>>(`/api/cuentas/${id}/movimientos`, { params });
  }

  extracto(id: number, filtro: FiltroFechas): Observable<Extracto> {
    return this.http.get<Extracto>(`/api/cuentas/${id}/extracto`, {
      params: conFechas(new HttpParams(), filtro),
    });
  }

  /**
   * La clave de idempotencia la genera quien llama, una vez por transferencia, y se
   * reusa en los reintentos. Se pide la respuesta completa para leer Idempotent-Replayed.
   */
  transferir(
    datos: TransferenciaRequest,
    claveIdempotencia: string,
  ): Observable<HttpResponse<Transferencia>> {
    return this.http.post<Transferencia>('/api/transferencias', datos, {
      headers: { 'Idempotency-Key': claveIdempotencia },
      observe: 'response',
    });
  }

  // ---- Operador ----

  clientes(): Observable<Cliente[]> {
    return this.http.get<Cliente[]>('/api/clientes');
  }

  crearCliente(datos: ClienteRequest): Observable<Cliente> {
    return this.http.post<Cliente>('/api/clientes', datos);
  }

  crearCuenta(datos: CuentaRequest): Observable<Cuenta> {
    return this.http.post<Cuenta>('/api/cuentas', datos);
  }

  depositar(cuentaId: number, importe: number, descripcion: string | null): Observable<Movimiento> {
    return this.http.post<Movimiento>(`/api/cuentas/${cuentaId}/depositos`, {
      importe,
      descripcion,
    });
  }

  cambiarEstado(cuentaId: number, estado: EstadoCuenta): Observable<Cuenta> {
    return this.http.patch<Cuenta>(`/api/cuentas/${cuentaId}/estado`, { estado });
  }
}

function conFechas(params: HttpParams, filtro: FiltroFechas): HttpParams {
  let p = params;
  if (filtro.desde) {
    p = p.set('desde', filtro.desde);
  }
  if (filtro.hasta) {
    p = p.set('hasta', filtro.hasta);
  }
  return p;
}
