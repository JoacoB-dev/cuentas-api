package ar.cuentas.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.cbu")
public record CbuProperties(String entidad, String sucursal) {
}
