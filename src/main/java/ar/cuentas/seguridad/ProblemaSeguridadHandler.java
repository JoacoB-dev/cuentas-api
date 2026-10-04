package ar.cuentas.seguridad;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;

/**
 * Responde 401 y 403 con el mismo formato ProblemDetail (RFC 7807) que el resto
 * de la API. Estos errores ocurren en el filtro de seguridad, antes de llegar al
 * controller, por eso no los atrapa el @RestControllerAdvice.
 */
@Component
public class ProblemaSeguridadHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper mapper;

    public ProblemaSeguridadHandler(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException ex) throws IOException {
        response.setHeader("WWW-Authenticate", "Bearer");
        escribir(request, response, HttpStatus.UNAUTHORIZED, "no-autenticado",
                "Falta el token de acceso o es inválido/vencido. Iniciá sesión en POST /api/auth/login.");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException ex) throws IOException {
        escribir(request, response, HttpStatus.FORBIDDEN, "acceso-denegado",
                "Tu rol no tiene permiso para esta operación.");
    }

    private void escribir(HttpServletRequest request, HttpServletResponse response, HttpStatus estado,
                          String codigo, String detalle) throws IOException {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
        problema.setType(URI.create("urn:cuentas-api:error:" + codigo));
        problema.setTitle(estado == HttpStatus.UNAUTHORIZED ? "No autenticado" : "Acceso denegado");
        problema.setInstance(URI.create(request.getRequestURI()));
        problema.setProperty("codigo", codigo);
        response.setStatus(estado.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getOutputStream(), problema);
    }
}
