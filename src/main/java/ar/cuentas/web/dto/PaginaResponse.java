package ar.cuentas.web.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/** Página propia en vez de serializar Page de Spring (su JSON no es estable entre versiones). */
public record PaginaResponse<T>(List<T> contenido, int pagina, int tamanio, long totalElementos, int totalPaginas) {

    public static <E, T> PaginaResponse<T> de(Page<E> page, Function<E, T> mapeo) {
        return new PaginaResponse<>(page.getContent().stream().map(mapeo).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
