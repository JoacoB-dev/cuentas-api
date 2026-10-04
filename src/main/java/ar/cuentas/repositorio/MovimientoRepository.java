package ar.cuentas.repositorio;

import ar.cuentas.dominio.Movimiento;
import ar.cuentas.dominio.TipoMovimiento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MovimientoRepository extends JpaRepository<Movimiento, Long> {

    /** Movimientos en [desde, hasta), del más nuevo al más viejo. */
    Page<Movimiento> findByCuentaIdAndFechaGreaterThanEqualAndFechaLessThanOrderByFechaDescIdDesc(
            Long cuentaId, Instant desde, Instant hasta, Pageable pageable);

    /** Para el extracto: en orden cronológico. */
    List<Movimiento> findByCuentaIdAndFechaGreaterThanEqualAndFechaLessThanOrderByFechaAscIdAsc(
            Long cuentaId, Instant desde, Instant hasta);

    /** Último movimiento antes de una fecha: su saldo posterior es el saldo inicial del período. */
    Optional<Movimiento> findFirstByCuentaIdAndFechaLessThanOrderByFechaDescIdDesc(Long cuentaId, Instant antes);

    @Query("""
            select coalesce(sum(m.importe), 0) from Movimiento m
            where m.cuentaId = :cuentaId and m.tipo = :tipo
              and m.fecha >= :desde and m.fecha < :hasta
            """)
    BigDecimal sumarPorTipo(@Param("cuentaId") Long cuentaId, @Param("tipo") TipoMovimiento tipo,
                            @Param("desde") Instant desde, @Param("hasta") Instant hasta);
}
