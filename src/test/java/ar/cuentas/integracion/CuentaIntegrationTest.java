package ar.cuentas.integracion;

import ar.cuentas.dominio.Cbu;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CuentaIntegrationTest extends BaseMockMvc {

    @Test
    void unClienteSoloVeSusCuentas() throws Exception {
        getCon("ana", "/api/cuentas")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].titular", containsInAnyOrder("Ana Gómez", "Ana Gómez")));
        getCon("operador", "/api/cuentas").andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    void unaCuentaAjenaSeRespondeComoInexistente() throws Exception {
        getCon("ana", "/api/cuentas/{id}", brunoArs.id())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("no-encontrado"));
        getCon("operador", "/api/cuentas/{id}", brunoArs.id()).andExpect(status().isOk());
    }

    @Test
    void elOperadorAbreUnaCuentaConCbuValidoGenerado() throws Exception {
        String r = postCon("operador", "/api/cuentas",
                "{\"clienteId\":" + anaId + ",\"tipo\":\"CA\",\"moneda\":\"USD\",\"limiteDiario\":3000}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.saldo").value(0))
                .andExpect(jsonPath("$.estado").value("ACTIVA"))
                .andReturn().getResponse().getContentAsString();
        String cbu = leer(r).get("cbu").asText();
        assertThat(cbu).hasSize(22).startsWith("9990001");
        assertThat(Cbu.esValido(cbu)).isTrue();
    }

    @Test
    void unClienteNoPuedeAbrirCuentasNiDepositar() throws Exception {
        postCon("ana", "/api/cuentas",
                "{\"clienteId\":" + anaId + ",\"tipo\":\"CA\",\"moneda\":\"USD\",\"limiteDiario\":3000}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("acceso-denegado"));
        postCon("ana", "/api/cuentas/" + anaArs.id() + "/depositos", "{\"importe\":1000}")
                .andExpect(status().isForbidden());
    }

    @Test
    void datosInvalidosDevuelven400ConLaListaDeErroresEnCastellano() throws Exception {
        String r = postCon("operador", "/api/cuentas", "{\"tipo\":\"CA\",\"limiteDiario\":-5}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("validacion"))
                .andExpect(jsonPath("$.errores[*].campo", hasItem("clienteId")))
                .andExpect(jsonPath("$.errores[*].mensaje", hasItem("El límite diario debe ser mayor a cero")))
                .andReturn().getResponse().getContentAsString();
        JsonNode errores = leer(r).get("errores");
        assertThat(errores).hasSize(3); // clienteId, moneda, limiteDiario
    }

    @Test
    void cajaDeAhorroConDescubiertoSeRechazaCon422() throws Exception {
        postCon("operador", "/api/cuentas", "{\"clienteId\":" + anaId
                + ",\"tipo\":\"CA\",\"moneda\":\"ARS\",\"limiteDiario\":1000,\"descubiertoAutorizado\":500}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("descubierto-en-caja-de-ahorro"));
    }

    @Test
    void depositoDelOperadorAcreditaYDejaMovimiento() throws Exception {
        postCon("operador", "/api/cuentas/" + anaArs.id() + "/depositos",
                "{\"importe\":1234.56,\"descripcion\":\"Depósito en efectivo\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("CREDITO"))
                .andExpect(jsonPath("$.saldoPosterior").value(51234.56));
        assertThat(saldo(anaArs.id())).isEqualByComparingTo("51234.56");
    }

    @Test
    void elOperadorBloqueaUnaCuenta() throws Exception {
        mvc.perform(conToken(patch("/api/cuentas/" + anaArs.id() + "/estado"), "operador")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"BLOQUEADA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("BLOQUEADA"));
        postCon("operador", "/api/cuentas/" + anaArs.id() + "/depositos", "{\"importe\":10}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("cuenta-bloqueada"));
    }
}
