package ar.cuentas.error;

/** Error del cliente de la API que no detecta Bean Validation (p. ej. rango de fechas al revés). */
public class SolicitudInvalidaException extends RuntimeException {

    public SolicitudInvalidaException(String mensaje) {
        super(mensaje);
    }
}
