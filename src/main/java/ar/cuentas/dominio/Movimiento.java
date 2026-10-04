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

/** Asiento en el historial de una cuenta. Nunca se modifica ni se borra. */
@Entity
@Table(name = "movimiento")
public class Movimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cuenta_id", nullable = false)
    private Long cuentaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoMovimiento tipo;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal importe;

    @Column(name = "saldo_posterior", nullable = false, precision = 19, scale = 2)
    private BigDecimal saldoPosterior;

    @Column(nullable = false)
    private String descripcion;

    @Column(name = "transferencia_id")
    private Long transferenciaId;

    @Column(nullable = false)
    private Instant fecha;

    protected Movimiento() {
    }

    public Movimiento(Long cuentaId, TipoMovimiento tipo, BigDecimal importe, BigDecimal saldoPosterior,
                      String descripcion, Long transferenciaId, Instant fecha) {
        this.cuentaId = cuentaId;
        this.tipo = tipo;
        this.importe = importe;
        this.saldoPosterior = saldoPosterior;
        this.descripcion = descripcion;
        this.transferenciaId = transferenciaId;
        this.fecha = fecha;
    }

    public Long getId() {
        return id;
    }

    public Long getCuentaId() {
        return cuentaId;
    }

    public TipoMovimiento getTipo() {
        return tipo;
    }

    public BigDecimal getImporte() {
        return importe;
    }

    public BigDecimal getSaldoPosterior() {
        return saldoPosterior;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public Long getTransferenciaId() {
        return transferenciaId;
    }

    public Instant getFecha() {
        return fecha;
    }
}
