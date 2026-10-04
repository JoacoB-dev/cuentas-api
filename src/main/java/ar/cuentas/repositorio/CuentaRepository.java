package ar.cuentas.repositorio;

import ar.cuentas.dominio.Cuenta;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CuentaRepository extends JpaRepository<Cuenta, Long> {

    /**
     * Lee la cuenta con SELECT ... FOR UPDATE. Bloquea la fila hasta el fin de la
     * transacción: cualquier otra transferencia que toque esta cuenta espera.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cuenta c where c.id = :id")
    Optional<Cuenta> buscarParaActualizar(@Param("id") Long id);

    @EntityGraph(attributePaths = "cliente")
    Optional<Cuenta> findWithClienteById(Long id);

    @EntityGraph(attributePaths = "cliente")
    Optional<Cuenta> findByCbu(String cbu);

    @EntityGraph(attributePaths = "cliente")
    List<Cuenta> findByClienteIdOrderByIdAsc(Long clienteId);

    @EntityGraph(attributePaths = "cliente")
    List<Cuenta> findAllByOrderByIdAsc();

    @Query("select c.id from Cuenta c where c.cbu = :cbu")
    Optional<Long> buscarIdPorCbu(@Param("cbu") String cbu);

    @Query(value = "select nextval('cuenta_numero_seq')", nativeQuery = true)
    long siguienteNumeroDeCuenta();
}
