/**
 * Genera una Idempotency-Key (UUID v4) para una transferencia.
 *
 * crypto.randomUUID() sólo existe en contextos seguros (https o localhost). Si la app
 * se abre por http desde otra máquina de la red, se arma el UUID a mano con
 * crypto.getRandomValues(), que sí está disponible.
 */
export function generarClaveIdempotencia(c: Crypto = globalThis.crypto): string {
  if (typeof c.randomUUID === 'function') {
    return c.randomUUID();
  }
  const b = c.getRandomValues(new Uint8Array(16));
  b[6] = (b[6] & 0x0f) | 0x40; // versión 4
  b[8] = (b[8] & 0x3f) | 0x80; // variante RFC 4122
  const hex = Array.from(b, (x) => x.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}
