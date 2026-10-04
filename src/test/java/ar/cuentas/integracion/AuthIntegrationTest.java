package ar.cuentas.integracion;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIntegrationTest extends BaseMockMvc {

    @Test
    void loginCorrectoDevuelveUnTokenQueSirveParaLlamarALaApi() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ana\",\"password\":\"ana123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.rol").value("CLIENTE"))
                .andExpect(jsonPath("$.token").isNotEmpty());

        getCon("ana", "/api/cuentas").andExpect(status().isOk());
    }

    @Test
    void contraseniaIncorrectaDevuelve401ConProblemDetail() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ana\",\"password\":\"otra\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.codigo").value("credenciales-invalidas"))
                .andExpect(jsonPath("$.detail").value("Usuario o contraseña incorrectos."));

        // Un usuario inexistente recibe exactamente la misma respuesta.
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nadie\",\"password\":\"x\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("credenciales-invalidas"));
    }

    @Test
    void sinTokenDevuelve401() throws Exception {
        mvc.perform(get("/api/cuentas"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", startsWith("Bearer")))
                .andExpect(jsonPath("$.codigo").value("no-autenticado"));
    }

    @Test
    void tokenAdulteradoDevuelve401() throws Exception {
        String token = token("ana");
        String adulterado = token.substring(0, token.length() - 4) + "AAAA";
        mvc.perform(get("/api/cuentas").header("Authorization", "Bearer " + adulterado))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("no-autenticado"));
    }
}
