package ar.cuentas.servicio;

import ar.cuentas.dominio.Usuario;
import ar.cuentas.error.CredencialesInvalidasException;
import ar.cuentas.repositorio.UsuarioRepository;
import ar.cuentas.seguridad.TokenService;
import ar.cuentas.web.dto.LoginResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AuthService {

    private final String hashDeRelleno;

    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;

    public AuthService(UsuarioRepository usuarios, PasswordEncoder passwordEncoder, TokenService tokens) {
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        // Hash de una contraseña cualquiera, para comparar cuando el usuario no existe.
        this.hashDeRelleno = passwordEncoder.encode("relleno-para-usuarios-inexistentes");
    }

    @Transactional(readOnly = true)
    public LoginResponse login(String username, String password) {
        Optional<Usuario> usuario = usuarios.findByUsername(username);
        // Se compara siempre (aunque el usuario no exista) para que el tiempo de
        // respuesta no revele qué usuarios existen.
        String hash = usuario.map(Usuario::getPasswordHash).orElse(hashDeRelleno);
        boolean coincide = passwordEncoder.matches(password, hash);
        if (usuario.isEmpty() || !coincide) {
            throw new CredencialesInvalidasException();
        }
        TokenService.TokenEmitido token = tokens.emitir(usuario.get());
        return new LoginResponse(token.token(), "Bearer", token.vence(), usuario.get().getRol().name());
    }
}
