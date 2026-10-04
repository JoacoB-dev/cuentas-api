package ar.cuentas.web;

import ar.cuentas.seguridad.UsuarioActual;
import ar.cuentas.servicio.ClienteService;
import ar.cuentas.web.dto.ClienteRequest;
import ar.cuentas.web.dto.ClienteResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/clientes")
@Tag(name = "Clientes")
public class ClienteController {

    private final ClienteService clienteService;

    public ClienteController(ClienteService clienteService) {
        this.clienteService = clienteService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('OPERADOR')")
    @Operation(summary = "Da de alta un cliente (OPERADOR)")
    public ClienteResponse crear(@Valid @RequestBody ClienteRequest req) {
        return clienteService.crear(req);
    }

    @GetMapping
    @PreAuthorize("hasRole('OPERADOR')")
    @Operation(summary = "Lista todos los clientes (OPERADOR)")
    public List<ClienteResponse> listar() {
        return clienteService.listar();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Datos de un cliente (un CLIENTE sólo puede verse a sí mismo)")
    public ClienteResponse obtener(@PathVariable Long id, @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return clienteService.obtener(id, UsuarioActual.desde(jwt));
    }
}
