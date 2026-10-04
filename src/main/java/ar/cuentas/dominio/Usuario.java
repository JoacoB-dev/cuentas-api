package ar.cuentas.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "usuario")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Rol rol;

    /** Sólo para usuarios CLIENTE. */
    @Column(name = "cliente_id")
    private Long clienteId;

    protected Usuario() {
    }

    public Usuario(String username, String passwordHash, Rol rol, Long clienteId) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.rol = rol;
        this.clienteId = clienteId;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Rol getRol() {
        return rol;
    }

    public Long getClienteId() {
        return clienteId;
    }
}
