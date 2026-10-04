package ar.cuentas.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "API de cuentas y transferencias",
                version = "1.0.0",
                description = """
                        API REST de ejemplo: clientes, cuentas (CA/CC en ARS y USD), movimientos y \
                        transferencias con idempotencia y límite diario. Para probar: POST /api/auth/login \
                        con un usuario demo, copiar el token y usar el botón "Authorize"."""),
        security = @SecurityRequirement(name = "bearer"))
@SecurityScheme(name = "bearer", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {
}
