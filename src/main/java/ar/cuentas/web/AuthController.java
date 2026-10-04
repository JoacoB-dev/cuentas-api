package ar.cuentas.web;

import ar.cuentas.servicio.AuthService;
import ar.cuentas.web.dto.LoginRequest;
import ar.cuentas.web.dto.LoginResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticación")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @SecurityRequirements // no requiere token
    @Operation(summary = "Inicia sesión y devuelve un JWT",
            description = "Usuarios demo: ana / ana123 y bruno / bruno123 (CLIENTE), operador / operador123 (OPERADOR).")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req.username(), req.password());
    }
}
