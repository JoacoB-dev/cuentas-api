package ar.cuentas.web.dto;

import java.time.Instant;

public record LoginResponse(String token, String tipo, Instant venceEn, String rol) {
}
