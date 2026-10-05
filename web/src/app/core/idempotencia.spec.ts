import { generarClaveIdempotencia } from './idempotencia';

const UUID_V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

describe('generarClaveIdempotencia', () => {
  it('genera un UUID v4 distinto cada vez', () => {
    const a = generarClaveIdempotencia();
    const b = generarClaveIdempotencia();
    expect(a).toMatch(UUID_V4);
    expect(a).not.toBe(b);
  });

  it('funciona aunque crypto.randomUUID no exista (http fuera de localhost)', () => {
    const sinRandomUuid = {
      getRandomValues: <T extends ArrayBufferView<ArrayBuffer>>(a: T) =>
        globalThis.crypto.getRandomValues(a),
    } as unknown as Crypto;
    expect(generarClaveIdempotencia(sinRandomUuid)).toMatch(UUID_V4);
  });
});
