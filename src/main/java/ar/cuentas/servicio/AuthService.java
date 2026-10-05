package ar.cuentas.servicio;

import ar.cuentas.dominio.Usuario;
import ar.cuentas.error.CredencialesInvalidasException;
import ar.cuentas.repositorio.UsuarioRepository;
import ar.cuentas.seguridad.LimitadorDeIntentosDeLogin;
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
    private final LimitadorDeIntentosDeLogin limitador;

    public AuthService(UsuarioRepository usuarios, PasswordEncoder passwordEncoder, TokenService tokens,
                       LimitadorDeIntentosDeLogin limitador) {
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.limitador = limitador;
        // Hash de una contraseña cualquiera, para comparar cuando el usuario no existe.
        this.hashDeRelleno = passwordEncoder.encode("relleno-para-usuarios-inexistentes");
    }

    @Transactional(readOnly = true)
    public LoginResponse login(String username, String password) {
        // Antes de mirar la contraseña: un usuario bloqueado recibe 429 aunque la acierte.
        limitador.verificar(username);
        Optional<Usuario> usuario = usuarios.findByUsername(username);
        // Se compara siempre (aunque el usuario no exista) para que el tiempo de
        // respuesta no revele qué usuarios existen.
        String hash = usuario.map(Usuario::getPasswordHash).orElse(hashDeRelleno);
        boolean coincide = passwordEncoder.matches(password, hash);
        if (usuario.isEmpty() || !coincide) {
            // También cuenta para usuarios inexistentes: así no se distingue cuáles existen.
            limitador.registrarFallo(username);
            throw new CredencialesInvalidasException();
        }
        limitador.limpiar(username);
        TokenService.TokenEmitido token = tokens.emitir(usuario.get());
        return new LoginResponse(token.token(), "Bearer", token.vence(), usuario.get().getRol().name());
    }
}
