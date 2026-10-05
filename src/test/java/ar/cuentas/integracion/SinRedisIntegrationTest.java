package ar.cuentas.integracion;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La app apuntando a un Redis que no existe (puerto 1): tiene que seguir funcionando
 * sin caché y sin límite de login, en vez de devolver 500.
 */
@SpringBootTest(properties = "spring.data.redis.port=1")
class SinRedisIntegrationTest extends BaseMockMvc {

    @Override
    protected void limpiarRedis() {
        // No hay Redis en este test.
    }

    @Test
    void loginListadoYTransferenciaFuncionanSinRedis() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ana\",\"password\":\"mala\"}"))
                .andExpect(status().isUnauthorized());

        getCon("ana", "/api/cuentas")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].saldo").value(50000));
        transferir("ana", CLAVE, cuerpoTransferencia(anaArs.id(), brunoArs.cbu(), "100", null))
                .andExpect(status().isCreated());
        getCon("ana", "/api/cuentas").andExpect(jsonPath("$[0].saldo").value(49900));
    }
}
