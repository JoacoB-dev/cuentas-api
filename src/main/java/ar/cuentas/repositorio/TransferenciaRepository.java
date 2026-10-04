package ar.cuentas.repositorio;

import ar.cuentas.dominio.Transferencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

public interface TransferenciaRepository extends JpaRepository<Transferencia, Long> {

    Optional<Transferencia> findByUsuarioAndIdempotencyKey(String usuario, String idempotencyKey);

    /** Total enviado desde una cuenta a partir de un instante (inicio del día local). */
    @Query("""
            select coalesce(sum(t.importe), 0) from Transferencia t
            where t.cuentaOrigenId = :cuentaId and t.fecha >= :desde
            """)
    BigDecimal totalEnviadoDesde(@Param("cuentaId") Long cuentaId, @Param("desde") Instant desde);
}
