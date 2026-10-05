/** Fecha de hoy en Argentina, en formato AAAA-MM-DD (para inputs type="date"). */
export function hoyEnArgentina(ahora: Date = new Date()): string {
  // en-CA formatea como AAAA-MM-DD.
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Argentina/Buenos_Aires' }).format(
    ahora,
  );
}

/** Resta días a una fecha AAAA-MM-DD. */
export function restarDias(fecha: string, dias: number): string {
  const d = new Date(`${fecha}T12:00:00Z`);
  d.setUTCDate(d.getUTCDate() - dias);
  return d.toISOString().slice(0, 10);
}

export const NOMBRE_TIPO_CUENTA: Record<string, string> = {
  CA: 'Caja de ahorro',
  CC: 'Cuenta corriente',
};
