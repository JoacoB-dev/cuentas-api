package ar.cuentas.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Cuenta bancaria. Las reglas de saldo viven acá (y no en el servicio) para
 * poder probarlas sin base de datos.
 */
@Entity
@Table(name = "cuenta")
public class Cuenta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @Column(nullable = false, unique = true, length = 22)
    private String cbu;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoCuenta tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Moneda moneda;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoCuenta estado;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal saldo;

    @Column(name = "descubierto_autorizado", nullable = false, precision = 19, scale = 2)
    private BigDecimal descubiertoAutorizado;

    @Column(name = "limite_diario", nullable = false, precision = 19, scale = 2)
    private BigDecimal limiteDiario;

    @Column(name = "creada_en", nullable = false)
    private Instant creadaEn;

    protected Cuenta() {
    }

    public Cuenta(Cliente cliente, String cbu, TipoCuenta tipo, Moneda moneda,
                  BigDecimal descubiertoAutorizado, BigDecimal limiteDiario) {
        if (!Cbu.esValido(cbu)) {
            throw new IllegalArgumentException("CBU inválido: " + cbu);
        }
        BigDecimal descubierto = descubiertoAutorizado == null ? BigDecimal.ZERO : descubiertoAutorizado;
        if (tipo == TipoCuenta.CA && descubierto.signum() != 0) {
            throw new ReglaNegocioException("descubierto-en-caja-de-ahorro",
                    "Una caja de ahorro no puede tener descubierto autorizado.");
        }
        this.cliente = cliente;
        this.cbu = cbu;
        this.tipo = tipo;
        this.moneda = moneda;
        this.estado = EstadoCuenta.ACTIVA;
        this.saldo = BigDecimal.ZERO.setScale(2);
        this.descubiertoAutorizado = descubierto.setScale(2);
        this.limiteDiario = limiteDiario.setScale(2);
        this.creadaEn = Instant.now();
    }

    /** Lo que se puede debitar ahora: saldo más descubierto acordado. */
    public BigDecimal disponible() {
        return saldo.add(descubiertoAutorizado);
    }

    public void debitar(BigDecimal importe) {
        validarImporte(importe);
        verificarActiva();
        if (disponible().compareTo(importe) < 0) {
            throw new ReglaNegocioException("saldo-insuficiente",
                    "Saldo insuficiente en la cuenta " + cbu + ": disponible " + disponible()
                            + " " + moneda + ", se intentó debitar " + importe + " " + moneda + ".");
        }
        saldo = saldo.subtract(importe);
    }

    public void acreditar(BigDecimal importe) {
        validarImporte(importe);
        verificarActiva();
        saldo = saldo.add(importe);
    }

    public void verificarActiva() {
        if (estado != EstadoCuenta.ACTIVA) {
            throw new ReglaNegocioException("cuenta-bloqueada",
                    "La cuenta " + cbu + " está bloqueada y no admite movimientos.");
        }
    }

    public void cambiarEstado(EstadoCuenta nuevo) {
        this.estado = nuevo;
    }

    public boolean perteneceA(Long clienteId) {
        return clienteId != null && clienteId.equals(cliente.getId());
    }

    private static void validarImporte(BigDecimal importe) {
        if (importe == null || importe.signum() <= 0) {
            throw new ReglaNegocioException("importe-invalido", "El importe debe ser mayor a cero.");
        }
        if (importe.scale() > 2 && importe.stripTrailingZeros().scale() > 2) {
            throw new ReglaNegocioException("importe-invalido", "El importe admite como máximo 2 decimales.");
        }
    }

    public Long getId() {
        return id;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public String getCbu() {
        return cbu;
    }

    public TipoCuenta getTipo() {
        return tipo;
    }

    public Moneda getMoneda() {
        return moneda;
    }

    public EstadoCuenta getEstado() {
        return estado;
    }

    public BigDecimal getSaldo() {
        return saldo;
    }

    public BigDecimal getDescubiertoAutorizado() {
        return descubiertoAutorizado;
    }

    public BigDecimal getLimiteDiario() {
        return limiteDiario;
    }

    public Instant getCreadaEn() {
        return creadaEn;
    }
}
