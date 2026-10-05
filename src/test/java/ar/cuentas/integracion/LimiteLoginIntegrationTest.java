package ar.cuentas.integracion;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Límite de intentos de login con contadores en un Redis real (5 fallos cada 15 minutos). */
class LimiteLoginIntegrationTest extends BaseMockMvc {

    private static final String CLAVE_ANA = "cuentas-api:login-fallidos:ana";

    private ResultActions login(String usuario, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + usuario + "\",\"password\":\"" + password + "\"}"));
    }

    @Test
    void despuesDeCincoFallosElUsuarioQuedaBloqueadoAunqueAcierteLaContrasenia() throws Exception {
        for (int i = 0; i < 5; i++) {
            login("ana", "mala-" + i).andExpect(status().isUnauthorized());
        }

        login("ana", "ana123")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.codigo").value("demasiados-intentos"))
                .andExpect(jsonPath("$.detail", containsString("Probá de nuevo en 15 minutos")));

        // Los demás usuarios no se ven afectados.
        login("bruno", "bruno123").andExpect(status().isOk());
    }

    @Test
    void elContadorTieneVencimientoYUnLoginCorrectoLoReinicia() throws Exception {
        login("ana", "mala").andExpect(status().isUnauthorized());
        login("ana", "mala").andExpect(status().isUnauthorized());

        assertThat(redis.opsForValue().get(CLAVE_ANA)).isEqualTo("2");
        Long ttl = redis.getExpire(CLAVE_ANA);
        assertThat(ttl).isPositive().isLessThanOrEqualTo(15 * 60);

        login("ana", "ana123").andExpect(status().isOk());
        assertThat(redis.hasKey(CLAVE_ANA)).isFalse();
    }

    @Test
    void mayusculasYEspaciosCuentanComoElMismoUsuario() throws Exception {
        for (int i = 0; i < 5; i++) {
            login(i % 2 == 0 ? "ANA" : " ana ", "mala").andExpect(status().isUnauthorized());
        }
        login("ana", "ana123").andExpect(status().isTooManyRequests());
    }

    @Test
    void cuandoVenceLaVentanaSePuedeVolverAEntrar() throws Exception {
        for (int i = 0; i < 5; i++) {
            login("ana", "mala").andExpect(status().isUnauthorized());
        }
        login("ana", "ana123").andExpect(status().isTooManyRequests());

        // Simula el paso del tiempo: Redis borra la clave al vencer el TTL.
        redis.expire(CLAVE_ANA, Duration.ofMillis(1));
        Thread.sleep(20);

        login("ana", "ana123").andExpect(status().isOk());
    }
}
