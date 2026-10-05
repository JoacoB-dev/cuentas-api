import { FormControl, FormGroup } from '@angular/forms';
import { cbuValido, cbuValidator, importeValidator, rangoDeFechasValidator } from './validadores';

describe('validadores', () => {
  it('cbuValido acepta CBU reales y los de la demo', () => {
    expect(cbuValido('2850590940090418135201')).toBe(true);
    expect(cbuValido('9990001800000000010025')).toBe(true);
  });

  it('cbuValido rechaza verificador incorrecto, largo o letras', () => {
    expect(cbuValido('2850590940090418135202')).toBe(false);
    expect(cbuValido('285059094009041813520')).toBe(false);
    expect(cbuValido('28505909400904181352AB')).toBe(false);
  });

  it('cbuValidator deja vacío a cargo de required', () => {
    expect(cbuValidator(new FormControl(''))).toBeNull();
    expect(cbuValidator(new FormControl('123'))).toEqual({ cbu: true });
  });

  it('importeValidator exige positivo con hasta 2 decimales', () => {
    expect(importeValidator(new FormControl(1500.5))).toBeNull();
    expect(importeValidator(new FormControl('0'))).toEqual({ importe: true });
    expect(importeValidator(new FormControl(-3))).toEqual({ importe: true });
    expect(importeValidator(new FormControl('10.123'))).toEqual({ importe: true });
  });

  it('rangoDeFechasValidator detecta desde > hasta', () => {
    const grupo = (desde: string, hasta: string) =>
      new FormGroup({ desde: new FormControl(desde), hasta: new FormControl(hasta) });
    expect(rangoDeFechasValidator(grupo('2026-01-01', '2026-01-31'))).toBeNull();
    expect(rangoDeFechasValidator(grupo('2026-02-01', '2026-01-31'))).toEqual({ rango: true });
  });
});
