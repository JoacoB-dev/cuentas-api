package ar.cuentas.servicio;

import ar.cuentas.dominio.Cliente;
import ar.cuentas.dominio.ReglaNegocioException;
import ar.cuentas.error.NoEncontradoException;
import ar.cuentas.repositorio.ClienteRepository;
import ar.cuentas.seguridad.UsuarioActual;
import ar.cuentas.web.dto.ClienteRequest;
import ar.cuentas.web.dto.ClienteResponse;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ClienteService {

    private final ClienteRepository clientes;

    public ClienteService(ClienteRepository clientes) {
        this.clientes = clientes;
    }

    @Transactional
    public ClienteResponse crear(ClienteRequest req) {
        if (clientes.existsByDni(req.dni())) {
            throw new ReglaNegocioException("dni-duplicado", "Ya existe un cliente con DNI " + req.dni() + ".");
        }
        Cliente cliente = clientes.save(new Cliente(req.nombre().trim(), req.apellido().trim(), req.dni(),
                req.email().trim().toLowerCase()));
        return ClienteResponse.de(cliente);
    }

    @Transactional(readOnly = true)
    public List<ClienteResponse> listar() {
        return clientes.findAll(Sort.by("id")).stream().map(ClienteResponse::de).toList();
    }

    /** Un CLIENTE sólo puede verse a sí mismo; si pide otro id recibe 404. */
    @Transactional(readOnly = true)
    public ClienteResponse obtener(Long id, UsuarioActual usuario) {
        if (!usuario.esOperador() && !id.equals(usuario.clienteId())) {
            throw new NoEncontradoException("No existe el cliente " + id + " o no tenés acceso a él.");
        }
        return clientes.findById(id).map(ClienteResponse::de)
                .orElseThrow(() -> new NoEncontradoException("No existe el cliente " + id + "."));
    }
}
