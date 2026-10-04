package ar.cuentas.integracion;

import ar.cuentas.dominio.EstadoCuenta;
import ar.cuentas.dominio.Moneda;
import ar.cuentas.dominio.TipoCuenta;
import ar.cuentas.web.dto.CuentaResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransferenciaIntegrationTest extends BaseMockMvc {

    @Test
    void transfiereEntreCuentasYDejaMovimientosEnAmbas() throws Exception {
        String r = transferir("ana", CLAVE, cuerpoTransferencia(anaArs.id(), brunoArs.cbu(), "1500.50", "Alquiler"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "false"))
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.importe").value(1500.50))
                .andExpect(jsonPath("$.moneda").value("ARS"))
                .andReturn().getResponse().getContentAsString();
        long id = leer(r).get("id").asLong();

        assertThat(saldo(anaArs.id())).isEqualByComparingTo("48499.50");
        assertThat(saldo(brunoArs.id())).isEqualByComparingTo("21500.50");
        assertThat(contar("select count(*) from movimiento where transferencia_id = ?", id)).isEqualTo(2);

        // Bruno (destino) puede ver la transferencia; un tercero no.
        getCon("bruno", "/api/transferencias/{id}", id).andExpect(status().isOk());
    }

    @Test
    void saldoInsuficienteDevuelve422YNoCambiaNada() throws Exception {
        transferir("ana", CLAVE, cuerpoTransferencia(anaArs.id(), brunoArs.cbu(), "50000.01", null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("saldo-insuficiente"))
                .andExpect(jsonPath("$.type").value("urn:cuentas-api:error:saldo-insuficiente"));

        assertThat(saldo(anaArs.id())).isEqualByComparingTo("50000");
        assertThat(saldo(brunoArs.id())).isEqualByComparingTo("20000");
        assertThat(contar("select count(*) from transferencia")).isZero();
    }

    @Test
    void cuentaCorrientePuedeTransferirUsandoElDescubierto() throws Exception {
        // Bruno tiene 20.000 + 10.000 de descubierto.
        transferir("bruno", CLAVE, cuerpoTransferencia(brunoArs.id(), anaArs.cbu(), "30000", null))
                .andExpect(status().isCreated());
        assertThat(saldo(brunoArs.id())).isEqualByComparingTo("-10000");

        transferir("bruno", "otra", cuerpoTransferencia(brunoArs.id(), anaArs.cbu(), "0.01", null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("saldo-insuficiente"));
    }

    @Test
    void monedasDistintasSeRechazan() throws Exception {
        transferir("ana", CLAVE, cuerpoTransferencia(anaArs.id(), anaUsd.cbu(), "10", null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("monedas-distintas"));
    }

    @Test
    void sinIdempotencyKeyDevuelve400() throws Exception {
        transferir("ana", null, cuerpoTransferencia(anaArs.id(), brunoArs.cbu(), "10", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Falta el header obligatorio Idempotency-Key."));
        assertThat(contar("select count(*) from transferencia")).isZero();
    }

    @Test
    void reintentoConLaMismaClaveNoTransfiereDosVeces() throws Exception {
        String cuerpo = cuerpoTransferencia(anaArs.id(), brunoArs.cbu(), "1000", "Cuota");
        String primera = transferir("ana", CLAVE, cuerpo)
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "false"))
                .andReturn().getResponse().getContentAsString();
        String segunda = transferir("ana", CLAVE, cuerpo)
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andReturn().getResponse().getContentAsString();

        assertThat(leer(segunda)).isEqualTo(leer(primera));
        assertThat(saldo(anaArs.id())).isEqualByComparingTo("49000");
        assertThat(contar("select count(*) from transferencia")).isEqualTo(1);
    }

    @Test
    void laMismaClaveConOtrosDatosSeRechaza() throws Exception {
        transferir("ana", CLAVE, cuerpoTransferencia(anaArs.id(), brunoArs.cbu(), "1000", null))
                .andExpect(status().isCreated());
        transferir("ana", CLAVE, cuerpoTransferencia(anaArs.id(), brunoArs.cbu(), "2000", null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("clave-idempotencia-reutilizada"));
        assertThat(saldo(anaArs.id())).isEqualByComparingTo("49000");
    }

    @Test
    void laClaveEsPorUsuario() throws Exception {
        // Dos usuarios distintos pueden usar la misma clave sin pisarse.
        transferir("ana", CLAVE, cuerpoTransferencia(anaArs.id(), brunoArs.cbu(), "100", null))
                .andExpect(status().isCreated());
        transferir("bruno", CLAVE, cuerpoTransferencia(brunoArs.id(), anaArs.cbu(), "100", null))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "false"));
        assertThat(contar("select count(*) from transferencia")).isEqualTo(2);
    }

    @Test
    void respetaElLimiteDiario() throws Exception {
        CuentaResponse chica = crearCuenta(anaId, TipoCuenta.CA, Moneda.ARS, "1000", null, "5000");
        transferir("ana", "k1", cuerpoTransferencia(chica.id(), brunoArs.cbu(), "600", null))
                .andExpect(status().isCreated());
        transferir("ana", "k2", cuerpoTransferencia(chica.id(), brunoArs.cbu(), "400.01", null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("limite-diario-excedido"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("400.00")));
        transferir("ana", "k3", cuerpoTransferencia(chica.id(), brunoArs.cbu(), "400", null))
                .andExpect(status().isCreated());
        assertThat(saldo(chica.id())).isEqualByComparingTo("4000");
    }

    @Test
    void noSePuedeTransferirAUnaCuentaBloqueada() throws Exception {
        cuentaService.cambiarEstado(brunoArs.id(), EstadoCuenta.BLOQUEADA);
        transferir("ana", CLAVE, cuerpoTransferencia(anaArs.id(), brunoArs.cbu(), "10", null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("cuenta-bloqueada"));
        assertThat(saldo(anaArs.id())).isEqualByComparingTo("50000");
    }

    @Test
    void unClienteNoPuedeTransferirDesdeUnaCuentaAjena() throws Exception {
        transferir("ana", CLAVE, cuerpoTransferencia(brunoArs.id(), anaArs.cbu(), "10", null))
                .andExpect(status().isNotFound());
        assertThat(saldo(brunoArs.id())).isEqualByComparingTo("20000");
    }

    @Test
    void cbuConDigitoVerificadorIncorrectoDevuelve400YCbuInexistente404() throws Exception {
        String cbu = brunoArs.cbu();
        String alterado = cbu.substring(0, 21) + ((cbu.charAt(21) - '0' + 1) % 10);
        transferir("ana", CLAVE, cuerpoTransferencia(anaArs.id(), alterado, "10", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("cbuDestino"));
        // CBU bien formado pero que no corresponde a ninguna cuenta de este banco.
        transferir("ana", CLAVE, cuerpoTransferencia(anaArs.id(), "2850590940090418135201", "10", null))
                .andExpect(status().isNotFound());
    }
}
