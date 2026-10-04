package ar.cuentas.web;

import ar.cuentas.seguridad.UsuarioActual;
import ar.cuentas.servicio.TransferenciaService;
import ar.cuentas.web.dto.TransferenciaRequest;
import ar.cuentas.web.dto.TransferenciaResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/transferencias")
@Tag(name = "Transferencias")
public class TransferenciaController {

    public static final String HEADER_IDEMPOTENCIA = "Idempotency-Key";
    public static final String HEADER_REPETIDA = "Idempotent-Replayed";

    private final TransferenciaService transferenciaService;

    public TransferenciaController(TransferenciaService transferenciaService) {
        this.transferenciaService = transferenciaService;
    }

    @PostMapping
    @Operation(summary = "Transfiere entre dos cuentas de la misma moneda",
            description = """
                    Requiere el header Idempotency-Key (por ejemplo un UUID generado por el cliente). \
                    Si se reenvía el mismo pedido con la misma clave, no se transfiere de nuevo: se devuelve \
                    la transferencia original con el header Idempotent-Replayed: true. \
                    Un CLIENTE sólo puede transferir desde sus propias cuentas.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Transferencia hecha (o reintento de una ya hecha)"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos o falta Idempotency-Key",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Cuenta de origen o CBU de destino inexistente",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "422", description = "Regla de negocio: saldo insuficiente, monedas distintas, "
                    + "límite diario, cuenta bloqueada o clave reutilizada con otros datos",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<TransferenciaResponse> transferir(
            @Parameter(description = "Clave única por transferencia (máx. 100 caracteres), p. ej. un UUID")
            @RequestHeader(HEADER_IDEMPOTENCIA) String idempotencyKey,
            @Valid @RequestBody TransferenciaRequest req,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        TransferenciaService.Resultado r = transferenciaService.transferir(req, idempotencyKey, UsuarioActual.desde(jwt));
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/transferencias/" + r.transferencia().id()))
                .header(HEADER_REPETIDA, String.valueOf(r.repetida()))
                .body(r.transferencia());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle de una transferencia (un CLIENTE sólo ve las que lo involucran)")
    public TransferenciaResponse obtener(@PathVariable Long id, @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return transferenciaService.obtener(id, UsuarioActual.desde(jwt));
    }
}
