package ar.cuentas.dominio;

/**
 * Validación y generación de CBU (Clave Bancaria Uniforme) con el algoritmo real
 * del BCRA.
 *
 * <p>Un CBU tiene 22 dígitos en dos bloques:
 * <ul>
 *   <li>Bloque 1 (8 dígitos): entidad (3) + sucursal (4) + dígito verificador.
 *       Ponderadores sobre los 7 primeros: 7, 1, 3, 9, 7, 1, 3.</li>
 *   <li>Bloque 2 (14 dígitos): número de cuenta (13) + dígito verificador.
 *       Ponderadores sobre los 13 primeros: 3, 9, 7, 1, 3, 9, 7, 1, 3, 9, 7, 1, 3.</li>
 * </ul>
 * En ambos casos el verificador es {@code (10 - (suma % 10)) % 10}, donde suma es
 * la suma de cada dígito multiplicado por su ponderador.
 */
public final class Cbu {

    private static final int[] PESOS_BLOQUE_1 = {7, 1, 3, 9, 7, 1, 3};
    private static final int[] PESOS_BLOQUE_2 = {3, 9, 7, 1, 3, 9, 7, 1, 3, 9, 7, 1, 3};

    private Cbu() {
    }

    public static boolean esValido(String cbu) {
        if (cbu == null || cbu.length() != 22 || !soloDigitos(cbu)) {
            return false;
        }
        String bloque1 = cbu.substring(0, 8);
        String bloque2 = cbu.substring(8);
        return digitoVerificador(bloque1.substring(0, 7), PESOS_BLOQUE_1) == bloque1.charAt(7) - '0'
                && digitoVerificador(bloque2.substring(0, 13), PESOS_BLOQUE_2) == bloque2.charAt(13) - '0';
    }

    /**
     * Arma un CBU válido a partir de entidad (3 dígitos), sucursal (4) y número de cuenta.
     * El número de cuenta se completa con ceros a la izquierda hasta 13 dígitos.
     */
    public static String generar(String entidad, String sucursal, long numeroCuenta) {
        if (entidad == null || entidad.length() != 3 || !soloDigitos(entidad)) {
            throw new IllegalArgumentException("La entidad debe tener 3 dígitos");
        }
        if (sucursal == null || sucursal.length() != 4 || !soloDigitos(sucursal)) {
            throw new IllegalArgumentException("La sucursal debe tener 4 dígitos");
        }
        if (numeroCuenta < 0 || numeroCuenta > 9_999_999_999_999L) {
            throw new IllegalArgumentException("El número de cuenta debe tener como máximo 13 dígitos");
        }
        String base1 = entidad + sucursal;
        String base2 = String.format("%013d", numeroCuenta);
        return base1 + digitoVerificador(base1, PESOS_BLOQUE_1)
                + base2 + digitoVerificador(base2, PESOS_BLOQUE_2);
    }

    static int digitoVerificador(String digitos, int[] pesos) {
        int suma = 0;
        for (int i = 0; i < pesos.length; i++) {
            suma += (digitos.charAt(i) - '0') * pesos[i];
        }
        return (10 - suma % 10) % 10;
    }

    private static boolean soloDigitos(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) < '0' || s.charAt(i) > '9') {
                return false;
            }
        }
        return true;
    }
}
