package ar.cuentas.integracion;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tests HTTP con MockMvc pasando por toda la cadena: filtros de seguridad, validación, JPA y PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
abstract class BaseMockMvc extends BaseIntegracion {

    @Autowired
    protected MockMvc mvc;

    protected String token(String usuario) throws Exception {
        String respuesta = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + usuario + "\",\"password\":\"" + usuario + "123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return leer(respuesta).get("token").asText();
    }

    protected ResultActions getCon(String usuario, String url, Object... vars) throws Exception {
        return mvc.perform(conToken(get(url, vars), usuario));
    }

    protected ResultActions postCon(String usuario, String url, String cuerpo) throws Exception {
        return mvc.perform(conToken(post(url), usuario).contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    protected ResultActions transferir(String usuario, String clave, String cuerpo) throws Exception {
        MockHttpServletRequestBuilder req = conToken(post("/api/transferencias"), usuario)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        if (clave != null) {
            req.header("Idempotency-Key", clave);
        }
        return mvc.perform(req);
    }

    protected MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder req, String usuario)
            throws Exception {
        return req.header("Authorization", "Bearer " + token(usuario));
    }
}
