package ar.cuentas.repositorio;

import ar.cuentas.dominio.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    boolean existsByDni(String dni);
}
