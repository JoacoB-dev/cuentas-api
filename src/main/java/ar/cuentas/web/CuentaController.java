package ar.cuentas.web;

import ar.cuentas.seguridad.UsuarioActual;
import ar.cuentas.servicio.CuentaService;
import ar.cuentas.servicio.MovimientoService;
import ar.cuentas.web.dto.CambioEstadoRequest;
import ar.cuentas.web.dto.CuentaRequest;
import ar.cuentas.web.dto.CuentaResponse;
import ar.cuentas.web.dto.DepositoRequest;
import ar.cuentas.web.dto.ExtractoResponse;
import ar.cuentas.web.dto.MovimientoResponse;
import ar.cuentas.web.dto.PaginaResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/cuentas")
@Tag(name = "Cuentas")
public class CuentaController {

    private final CuentaService cuentaService;
    private final MovimientoService movimientoService;

    public CuentaController(CuentaService cuentaService, MovimientoService movimientoService) {
        this.cuentaService = cuentaService;
        this.movimientoService = movimientoService;
    }

    @GetMapping
    @Operation(summary = "Lista cuentas",
            description = "Un CLIENTE ve sólo las suyas. Un OPERADOR ve todas y puede filtrar por clienteId.")
    public List<CuentaResponse> listar(@RequestParam(required = false) Long clienteId,
                                       @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return cuentaService.listar(UsuarioActual.desde(jwt), clienteId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle de una cuenta")
    public CuentaResponse obtener(@PathVariable Long id, @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return cuentaService.obtener(id, UsuarioActual.desde(jwt));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('OPERADOR')")
    @Operation(summary = "Abre una cuenta para un cliente (OPERADOR)", description = "El CBU se genera solo.")
    public CuentaResponse crear(@Valid @RequestBody CuentaRequest req) {
        return cuentaService.crear(req);
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasRole('OPERADOR')")
    @Operation(summary = "Bloquea o reactiva una cuenta (OPERADOR)")
    public CuentaResponse cambiarEstado(@PathVariable Long id, @Valid @RequestBody CambioEstadoRequest req) {
        return cuentaService.cambiarEstado(id, req.estado());
    }

    @PostMapping("/{id}/depositos")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('OPERADOR')")
    @Operation(summary = "Registra un depósito por ventanilla (OPERADOR)")
    public MovimientoResponse depositar(@PathVariable Long id, @Valid @RequestBody DepositoRequest req) {
        return cuentaService.depositar(id, req.importe(), req.descripcion());
    }

    @GetMapping("/{id}/movimientos")
    @Operation(summary = "Historial paginado de movimientos",
            description = "Fechas en formato AAAA-MM-DD, inclusivas, en hora argentina. "
                    + "Por defecto, los últimos 30 días. Orden: más nuevo primero.")
    public PaginaResponse<MovimientoResponse> movimientos(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "La página no puede ser negativa") int pagina,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "El tamaño mínimo es 1")
            @Max(value = 100, message = "El tamaño máximo es 100") int tamanio,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return movimientoService.historial(id, desde, hasta, pagina, tamanio, UsuarioActual.desde(jwt));
    }

    @GetMapping("/{id}/extracto")
    @Operation(summary = "Extracto de un período",
            description = "Saldo inicial, totales de créditos y débitos, saldo final y movimientos en orden cronológico.")
    public ExtractoResponse extracto(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return movimientoService.extracto(id, desde, hasta, UsuarioActual.desde(jwt));
    }
}
