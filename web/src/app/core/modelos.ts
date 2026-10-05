// Tipos que devuelve la API (ver los DTO en src/main/java/ar/cuentas/web/dto).
// Los importes llegan como number en el JSON; para mostrarlos alcanza, y nunca se
// hacen cuentas con ellos en el cliente (los cálculos los hace la API con BigDecimal).

export type Rol = 'CLIENTE' | 'OPERADOR';
export type Moneda = 'ARS' | 'USD';
export type TipoCuenta = 'CA' | 'CC';
export type EstadoCuenta = 'ACTIVA' | 'BLOQUEADA';
export type TipoMovimiento = 'CREDITO' | 'DEBITO';

export interface LoginResponse {
  token: string;
  tipo: string;
  venceEn: string;
  rol: Rol;
}

export interface Cuenta {
  id: number;
  cbu: string;
  tipo: TipoCuenta;
  moneda: Moneda;
  estado: EstadoCuenta;
  saldo: number;
  disponible: number;
  descubiertoAutorizado: number;
  limiteDiario: number;
  clienteId: number;
  titular: string;
}

export interface Movimiento {
  id: number;
  fecha: string;
  tipo: TipoMovimiento;
  importe: number;
  saldoPosterior: number;
  descripcion: string;
  transferenciaId: number | null;
}

export interface Pagina<T> {
  contenido: T[];
  pagina: number;
  tamanio: number;
  totalElementos: number;
  totalPaginas: number;
}

export interface Extracto {
  cuentaId: number;
  cbu: string;
  moneda: Moneda;
  titular: string;
  desde: string;
  hasta: string;
  saldoInicial: number;
  totalCreditos: number;
  totalDebitos: number;
  saldoFinal: number;
  movimientos: Movimiento[];
}

export interface TransferenciaRequest {
  cuentaOrigenId: number;
  cbuDestino: string;
  importe: number;
  concepto?: string;
}

export interface Transferencia {
  id: number;
  cuentaOrigenId: number;
  cuentaDestinoId: number;
  importe: number;
  moneda: Moneda;
  concepto: string | null;
  fecha: string;
}

export interface Cliente {
  id: number;
  nombre: string;
  apellido: string;
  dni: string;
  email: string;
}

export type ClienteRequest = Omit<Cliente, 'id'>;

export interface CuentaRequest {
  clienteId: number;
  tipo: TipoCuenta;
  moneda: Moneda;
  limiteDiario: number;
  descubiertoAutorizado?: number | null;
}

/** Error de la API en formato RFC 7807 (application/problem+json). */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  codigo?: string;
  errores?: { campo: string; mensaje: string }[];
}
