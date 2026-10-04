package ar.cuentas.seguridad;

import ar.cuentas.config.JwtProperties;
import ar.cuentas.dominio.Usuario;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
public class TokenService {

    public static final String EMISOR = "cuentas-api";

    private final JwtEncoder encoder;
    private final JwtProperties propiedades;
    private final Clock reloj;

    public TokenService(JwtEncoder encoder, JwtProperties propiedades, Clock reloj) {
        this.encoder = encoder;
        this.propiedades = propiedades;
        this.reloj = reloj;
    }

    public TokenEmitido emitir(Usuario usuario) {
        Instant ahora = reloj.instant();
        Instant vence = ahora.plus(Duration.ofMinutes(propiedades.duracionMinutos()));
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(EMISOR)
                .subject(usuario.getUsername())
                .issuedAt(ahora)
                .expiresAt(vence)
                .claim("rol", usuario.getRol().name());
        if (usuario.getClienteId() != null) {
            claims.claim("clienteId", usuario.getClienteId());
        }
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
        return new TokenEmitido(token, vence);
    }

    public record TokenEmitido(String token, Instant vence) {
    }
}
