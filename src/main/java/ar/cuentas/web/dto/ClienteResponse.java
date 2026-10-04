package ar.cuentas.web.dto;

import ar.cuentas.dominio.Cliente;

public record ClienteResponse(Long id, String nombre, String apellido, String dni, String email) {

    public static ClienteResponse de(Cliente c) {
        return new ClienteResponse(c.getId(), c.getNombre(), c.getApellido(), c.getDni(), c.getEmail());
    }
}
