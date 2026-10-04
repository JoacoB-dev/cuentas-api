package ar.cuentas.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Reloj inyectable. El "día" del límite diario y los filtros por fecha se
 * calculan en hora argentina, aunque en la base todo se guarda en UTC.
 */
@Configuration
public class RelojConfig {

    @Bean
    public Clock reloj(@Value("${app.zona-horaria}") String zona) {
        return Clock.system(ZoneId.of(zona));
    }
}
