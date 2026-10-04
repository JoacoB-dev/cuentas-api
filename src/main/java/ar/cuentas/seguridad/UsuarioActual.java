package ar.cuentas.seguridad;

import ar.cuentas.dominio.Rol;
import org.springframework.security.oauth2.jwt.Jwt;

/** Datos del usuario autenticado, sacados de los claims del JWT. */
public record UsuarioActual(String username, Rol rol, Long clienteId) {

    public static UsuarioActual desde(Jwt jwt) {
        Number clienteId = jwt.getClaim("clienteId");
        return new UsuarioActual(
                jwt.getSubject(),
                Rol.valueOf(jwt.getClaimAsString("rol")),
                clienteId == null ? null : clienteId.longValue());
    }

    public boolean esOperador() {
        return rol == Rol.OPERADOR;
    }
}
