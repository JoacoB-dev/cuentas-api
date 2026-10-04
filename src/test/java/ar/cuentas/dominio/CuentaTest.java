package ar.cuentas.dominio;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CuentaTest {

    private static final Cliente CLIENTE = new Cliente("Ana", "Gómez", "30111222", "ana@example.com");
    private static final String CBU = Cbu.generar("999", "0001", 1000);

    private static Cuenta cajaDeAhorro(String saldoInicial) {
        Cuenta c = new Cuenta(CLIENTE, CBU, TipoCuenta.CA, Moneda.ARS, null, new BigDecimal("100000"));
        c.acreditar(new BigDecimal(saldoInicial));
        return c;
    }

    @Test
    void debitarDescuentaDelSaldo() {
        Cuenta c = cajaDeAhorro("1000.00");
        c.debitar(new BigDecimal("250.50"));
        assertThat(c.getSaldo()).isEqualByComparingTo("749.50");
    }

    @Test
    void cajaDeAhorroNoPuedeQuedarEnNegativo() {
        Cuenta c = cajaDeAhorro("100.00");
        assertThatThrownBy(() -> c.debitar(new BigDecimal("100.01")))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("Saldo insuficiente")
                .extracting("codigo").isEqualTo("saldo-insuficiente");
        assertThat(c.getSaldo()).isEqualByComparingTo("100.00");
    }

    @Test
    void cuentaCorrientePuedeUsarElDescubiertoHastaElLimite() {
        Cuenta c = new Cuenta(CLIENTE, CBU, TipoCuenta.CC, Moneda.ARS, new BigDecimal("500"), new BigDecimal("100000"));
        c.acreditar(new BigDecimal("100"));
        c.debitar(new BigDecimal("600"));
        assertThat(c.getSaldo()).isEqualByComparingTo("-500");
        assertThat(c.disponible()).isEqualByComparingTo("0");
        assertThatThrownBy(() -> c.debitar(new BigDecimal("0.01"))).isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void cajaDeAhorroNoAdmiteDescubierto() {
        assertThatThrownBy(() -> new Cuenta(CLIENTE, CBU, TipoCuenta.CA, Moneda.ARS, new BigDecimal("1"),
                new BigDecimal("1000")))
                .isInstanceOf(ReglaNegocioException.class)
                .extracting("codigo").isEqualTo("descubierto-en-caja-de-ahorro");
    }

    @Test
    void cuentaBloqueadaNoAdmiteMovimientos() {
        Cuenta c = cajaDeAhorro("1000");
        c.cambiarEstado(EstadoCuenta.BLOQUEADA);
        assertThatThrownBy(() -> c.debitar(BigDecimal.ONE)).extracting("codigo").isEqualTo("cuenta-bloqueada");
        assertThatThrownBy(() -> c.acreditar(BigDecimal.ONE)).extracting("codigo").isEqualTo("cuenta-bloqueada");
    }

    @Test
    void rechazaImportesNoPositivosYCbuInvalido() {
        Cuenta c = cajaDeAhorro("1000");
        assertThatThrownBy(() -> c.debitar(BigDecimal.ZERO)).extracting("codigo").isEqualTo("importe-invalido");
        assertThatThrownBy(() -> c.acreditar(new BigDecimal("-5"))).extracting("codigo").isEqualTo("importe-invalido");
        assertThatThrownBy(() -> new Cuenta(CLIENTE, "1234567890123456789012", TipoCuenta.CA, Moneda.ARS, null,
                BigDecimal.TEN)).isInstanceOf(IllegalArgumentException.class);
    }
}
