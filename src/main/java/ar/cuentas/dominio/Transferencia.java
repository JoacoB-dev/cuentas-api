package ar.cuentas.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Transferencia ejecutada. Sólo se guardan las exitosas: si una transferencia
 * falla, la transacción entera se deshace y no queda rastro (tampoco de la
 * clave de idempotencia, así que el cliente puede reintentar).
 */
@Entity
@Table(name = "transferencia")
public class Transferencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cuenta_origen_id", nullable = false)
    private Long cuentaOrigenId;

    @Column(name = "cuenta_destino_id", nullable = false)
    private Long cuentaDestinoId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal importe;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Moneda moneda;

    private String concepto;

    @Column(nullable = false)
    private String usuario;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "hash_solicitud", nullable = false, length = 64)
    private String hashSolicitud;

    @Column(nullable = false)
    private Instant fecha;

    protected Transferencia() {
    }

    public Transferencia(Long cuentaOrigenId, Long cuentaDestinoId, BigDecimal importe, Moneda moneda,
                         String concepto, String usuario, String idempotencyKey, String hashSolicitud,
                         Instant fecha) {
        this.cuentaOrigenId = cuentaOrigenId;
        this.cuentaDestinoId = cuentaDestinoId;
        this.importe = importe;
        this.moneda = moneda;
        this.concepto = concepto;
        this.usuario = usuario;
        this.idempotencyKey = idempotencyKey;
        this.hashSolicitud = hashSolicitud;
        this.fecha = fecha;
    }

    public Long getId() {
        return id;
    }

    public Long getCuentaOrigenId() {
        return cuentaOrigenId;
    }

    public Long getCuentaDestinoId() {
        return cuentaDestinoId;
    }

    public BigDecimal getImporte() {
        return importe;
    }

    public Moneda getMoneda() {
        return moneda;
    }

    public String getConcepto() {
        return concepto;
    }

    public String getUsuario() {
        return usuario;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getHashSolicitud() {
        return hashSolicitud;
    }

    public Instant getFecha() {
        return fecha;
    }
}
