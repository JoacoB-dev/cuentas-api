package ar.cuentas.error;

import ar.cuentas.dominio.ReglaNegocioException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.NonNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.util.List;

/**
 * Traduce todas las excepciones a ProblemDetail (RFC 7807) con mensajes en castellano.
 * Cada respuesta incluye "codigo", estable y pensado para que lo lea un programa,
 * y "detail", pensado para mostrarle a una persona.
 */
@RestControllerAdvice
public class ManejadorDeErrores extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ManejadorDeErrores.class);

    @ExceptionHandler(ReglaNegocioException.class)
    public ProblemDetail reglaDeNegocio(ReglaNegocioException ex, HttpServletRequest req) {
        return problema(HttpStatus.UNPROCESSABLE_ENTITY, ex.getCodigo(), "Operación rechazada", ex.getMessage(), req);
    }

    @ExceptionHandler(NoEncontradoException.class)
    public ProblemDetail noEncontrado(NoEncontradoException ex, HttpServletRequest req) {
        return problema(HttpStatus.NOT_FOUND, "no-encontrado", "Recurso no encontrado", ex.getMessage(), req);
    }

    @ExceptionHandler(SolicitudInvalidaException.class)
    public ProblemDetail solicitudInvalida(SolicitudInvalidaException ex, HttpServletRequest req) {
        return problema(HttpStatus.BAD_REQUEST, "solicitud-invalida", "Solicitud inválida", ex.getMessage(), req);
    }

    @ExceptionHandler(CredencialesInvalidasException.class)
    public ResponseEntity<ProblemDetail> credenciales(CredencialesInvalidasException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(problema(HttpStatus.UNAUTHORIZED, "credenciales-invalidas", "No autenticado",
                        ex.getMessage(), req));
    }

    /** Lo lanza @PreAuthorize cuando el rol no alcanza. */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail accesoDenegado(AccessDeniedException ex, HttpServletRequest req) {
        return problema(HttpStatus.FORBIDDEN, "acceso-denegado", "Acceso denegado",
                "Tu rol no tiene permiso para esta operación.", req);
    }

    /** Por ejemplo, dos altas simultáneas con el mismo DNI: la constraint UNIQUE gana. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail integridad(DataIntegrityViolationException ex, HttpServletRequest req) {
        log.warn("Violación de integridad: {}", ex.getMostSpecificCause().getMessage());
        return problema(HttpStatus.CONFLICT, "conflicto", "Conflicto",
                "La operación entra en conflicto con datos existentes (por ejemplo, un valor que debe ser único).", req);
    }

    /**
     * No debería pasar (los locks se toman siempre en el mismo orden), pero si la base
     * aborta una transacción por deadlock o timeout de lock, se informa como reintentable.
     */
    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> lockFallido(PessimisticLockingFailureException ex, HttpServletRequest req) {
        log.warn("No se pudo obtener un lock: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header(HttpHeaders.RETRY_AFTER, "1")
                .body(problema(HttpStatus.SERVICE_UNAVAILABLE, "reintentar", "Reintentar",
                        "La cuenta está siendo usada por otra operación. Reintentá con la misma Idempotency-Key.", req));
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail inesperado(Exception ex, HttpServletRequest req) {
        log.error("Error no controlado en {} {}", req.getMethod(), req.getRequestURI(), ex);
        return problema(HttpStatus.INTERNAL_SERVER_ERROR, "error-interno", "Error interno",
                "Ocurrió un error inesperado. Si persiste, contactá a soporte.", req);
    }

    // ---- Errores que Spring MVC detecta antes de llegar al controller ----

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            @NonNull MethodArgumentNotValidException ex, @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status, @NonNull WebRequest request) {
        List<ErrorDeCampo> errores = ex.getBindingResult().getFieldErrors().stream()
                .map(this::errorDeCampo)
                .toList();
        ProblemDetail p = problema(HttpStatus.BAD_REQUEST, "validacion", "Datos inválidos",
                "Hay campos con errores. Revisá la lista \"errores\".", request);
        p.setProperty("errores", errores);
        return ResponseEntity.badRequest().body(p);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            @NonNull HandlerMethodValidationException ex, @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status, @NonNull WebRequest request) {
        List<ErrorDeCampo> errores = ex.getParameterValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream()
                        .map(e -> new ErrorDeCampo(String.valueOf(r.getMethodParameter().getParameterName()),
                                String.valueOf(e.getDefaultMessage()))))
                .toList();
        ProblemDetail p = problema(HttpStatus.BAD_REQUEST, "validacion", "Datos inválidos",
                "Hay parámetros con errores. Revisá la lista \"errores\".", request);
        p.setProperty("errores", errores);
        return ResponseEntity.badRequest().body(p);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            @NonNull HttpMessageNotReadableException ex, @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status, @NonNull WebRequest request) {
        return ResponseEntity.badRequest().body(problema(HttpStatus.BAD_REQUEST, "json-invalido",
                "Cuerpo ilegible", "El cuerpo no es un JSON válido o algún campo tiene un formato o valor no admitido.",
                request));
    }

    @Override
    protected ResponseEntity<Object> handleServletRequestBindingException(
            @NonNull ServletRequestBindingException ex, @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status, @NonNull WebRequest request) {
        String detalle = ex instanceof MissingRequestHeaderException h
                ? "Falta el header obligatorio " + h.getHeaderName() + "."
                : "La solicitud está incompleta: " + ex.getMessage();
        return ResponseEntity.badRequest().body(problema(HttpStatus.BAD_REQUEST, "solicitud-invalida",
                "Solicitud inválida", detalle, request));
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            @NonNull MissingServletRequestParameterException ex, @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status, @NonNull WebRequest request) {
        return ResponseEntity.badRequest().body(problema(HttpStatus.BAD_REQUEST, "solicitud-invalida",
                "Solicitud inválida", "Falta el parámetro obligatorio " + ex.getParameterName() + ".", request));
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            @NonNull TypeMismatchException ex, @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status, @NonNull WebRequest request) {
        String nombre = ex instanceof MethodArgumentTypeMismatchException m ? m.getName() : ex.getPropertyName();
        return ResponseEntity.badRequest().body(problema(HttpStatus.BAD_REQUEST, "solicitud-invalida",
                "Solicitud inválida", "El valor '" + ex.getValue() + "' no es válido para " + nombre
                        + ". Las fechas van en formato AAAA-MM-DD.", request));
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(
            @NonNull NoResourceFoundException ex, @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status, @NonNull WebRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problema(HttpStatus.NOT_FOUND, "no-encontrado",
                "Recurso no encontrado", "No existe el recurso pedido.", request));
    }

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
            @NonNull HttpRequestMethodNotSupportedException ex, @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status, @NonNull WebRequest request) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(problema(HttpStatus.METHOD_NOT_ALLOWED,
                "metodo-no-permitido", "Método no permitido",
                "El método " + ex.getMethod() + " no está permitido en este recurso.", request));
    }

    private ErrorDeCampo errorDeCampo(FieldError e) {
        return new ErrorDeCampo(e.getField(), String.valueOf(e.getDefaultMessage()));
    }

    /** Un error de validación de un campo o parámetro. */
    public record ErrorDeCampo(String campo, String mensaje) {
    }

    private static ProblemDetail problema(HttpStatus estado, String codigo, String titulo, String detalle,
                                          WebRequest request) {
        String uri = request instanceof ServletWebRequest s ? s.getRequest().getRequestURI() : null;
        return armar(estado, codigo, titulo, detalle, uri);
    }

    private static ProblemDetail problema(HttpStatus estado, String codigo, String titulo, String detalle,
                                          HttpServletRequest req) {
        return armar(estado, codigo, titulo, detalle, req.getRequestURI());
    }

    private static ProblemDetail armar(HttpStatus estado, String codigo, String titulo, String detalle, String uri) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(estado, detalle);
        p.setType(URI.create("urn:cuentas-api:error:" + codigo));
        p.setTitle(titulo);
        if (uri != null) {
            p.setInstance(URI.create(uri));
        }
        p.setProperty("codigo", codigo);
        return p;
    }
}
