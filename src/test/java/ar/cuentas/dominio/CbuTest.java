package ar.cuentas.dominio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CbuTest {

    @Test
    void aceptaUnCbuRealConDigitosVerificadoresCorrectos() {
        // CBU de ejemplo que circula en documentación pública (Banco Macro, entidad 285).
        assertThat(Cbu.esValido("2850590940090418135201")).isTrue();
    }

    @Test
    void calculaElDigitoDeCadaBloqueConLosPonderadoresDelBcra() {
        // Bloque 1 "2850590": 2*7+8*1+5*3+0*9+5*7+9*1+0*3 = 81 -> (10 - 1) % 10 = 9
        assertThat(Cbu.digitoVerificador("2850590", new int[]{7, 1, 3, 9, 7, 1, 3})).isEqualTo(9);
        // Bloque 2 "4009041813520": suma 139 -> (10 - 9) % 10 = 1
        assertThat(Cbu.digitoVerificador("4009041813520",
                new int[]{3, 9, 7, 1, 3, 9, 7, 1, 3, 9, 7, 1, 3})).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "2850590840090418135201", // verificador del bloque 1 alterado
            "2850590940090418135202", // verificador del bloque 2 alterado
            "285059094009041813520",  // 21 dígitos
            "28505909400904181352011", // 23 dígitos
            "28505909400904181352A1", // letra
            ""
    })
    void rechazaCbuMalFormadoOConVerificadorIncorrecto(String cbu) {
        assertThat(Cbu.esValido(cbu)).isFalse();
    }

    @Test
    void rechazaNull() {
        assertThat(Cbu.esValido(null)).isFalse();
    }

    @Test
    void generaCbusValidosParaCualquierNumeroDeCuenta() {
        for (long n = 0; n < 2_000; n += 7) {
            String cbu = Cbu.generar("999", "0001", n);
            assertThat(cbu).hasSize(22).startsWith("9990001");
            assertThat(Cbu.esValido(cbu)).as(cbu).isTrue();
        }
        assertThat(Cbu.generar("285", "0590", 4009041813520L)).isEqualTo("2850590940090418135201");
    }

    @Test
    void generarValidaLosParametros() {
        assertThatThrownBy(() -> Cbu.generar("99", "0001", 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Cbu.generar("999", "01", 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Cbu.generar("999", "0001", 10_000_000_000_000L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
