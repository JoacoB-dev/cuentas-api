import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/** Valida el CBU con el mismo algoritmo de dígitos verificadores que la API (Cbu.java). */
export function cbuValido(cbu: string): boolean {
  if (!/^\d{22}$/.test(cbu)) {
    return false;
  }
  const verificador = (digitos: string, pesos: number[]): number => {
    const suma = pesos.reduce((acc, peso, i) => acc + Number(digitos[i]) * peso, 0);
    return (10 - (suma % 10)) % 10;
  };
  const bloque1 = cbu.slice(0, 8);
  const bloque2 = cbu.slice(8);
  return (
    verificador(bloque1, [7, 1, 3, 9, 7, 1, 3]) === Number(bloque1[7]) &&
    verificador(bloque2, [3, 9, 7, 1, 3, 9, 7, 1, 3, 9, 7, 1, 3]) === Number(bloque2[13])
  );
}

export const cbuValidator: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const valor = String(control.value ?? '').trim();
  if (!valor) {
    return null; // de eso se encarga Validators.required
  }
  return cbuValido(valor) ? null : { cbu: true };
};

/** Importe positivo con hasta 2 decimales (igual que @Digits(fraction = 2) en la API). */
export const importeValidator: ValidatorFn = (
  control: AbstractControl,
): ValidationErrors | null => {
  const valor = control.value as number | string | null;
  if (valor === null || valor === '') {
    return null;
  }
  const texto = String(valor);
  if (!/^\d+(\.\d{1,2})?$/.test(texto) || Number(texto) <= 0) {
    return { importe: true };
  }
  return null;
};

/** Para un grupo con "desde" y "hasta" (AAAA-MM-DD): desde no puede ser posterior a hasta. */
export const rangoDeFechasValidator: ValidatorFn = (
  grupo: AbstractControl,
): ValidationErrors | null => {
  const desde = grupo.get('desde')?.value as string | null;
  const hasta = grupo.get('hasta')?.value as string | null;
  if (desde && hasta && desde > hasta) {
    return { rango: true };
  }
  return null;
};
