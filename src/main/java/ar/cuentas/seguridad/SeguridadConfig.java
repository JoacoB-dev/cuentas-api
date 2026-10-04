package ar.cuentas.seguridad;

import ar.cuentas.config.JwtProperties;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Seguridad stateless con JWT firmado con HMAC-SHA256.
 * Se usa el soporte de "resource server" de Spring Security para validar el
 * token (firma, vencimiento, emisor) en vez de escribir un filtro propio.
 */
@Configuration
@EnableMethodSecurity
public class SeguridadConfig {

    private static final Logger log = LoggerFactory.getLogger(SeguridadConfig.class);
    private static final String PREFIJO_SECRETO_DEV = "solo-desarrollo-local";

    @Bean
    public SecurityFilterChain cadenaDeFiltros(HttpSecurity http, ProblemaSeguridadHandler problemas)
            throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // API sin cookies: el token va en el header Authorization
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/actuator/health", "/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(rs -> rs
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(convertidorDeRoles()))
                        .authenticationEntryPoint(problemas)
                        .accessDeniedHandler(problemas))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(problemas)
                        .accessDeniedHandler(problemas));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey claveJwt) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(claveJwt));
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey claveJwt) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(claveJwt)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(TokenService.EMISOR)));
        return decoder;
    }

    /** El claim "rol" del token se convierte en la authority ROLE_CLIENTE / ROLE_OPERADOR. */
    private JwtAuthenticationConverter convertidorDeRoles() {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("rol");
        roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter convertidor = new JwtAuthenticationConverter();
        convertidor.setJwtGrantedAuthoritiesConverter(roles);
        return convertidor;
    }

    @Bean
    public SecretKey claveJwt(JwtProperties propiedades) {
        String secreto = propiedades.secreto();
        if (secreto == null || secreto.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET debe tener al menos 32 bytes (256 bits) para HS256.");
        }
        if (secreto.startsWith(PREFIJO_SECRETO_DEV)) {
            log.warn("Se está usando el secreto JWT de desarrollo. Definí JWT_SECRET fuera de tu máquina local.");
        }
        return new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
