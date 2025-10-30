package br.com.alexandria.alexandria_api.auth;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

  private final JdbcTemplate jdbcTemplate;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;

  public String login(String username, String password, HttpServletRequest request) {
    Map<String, Object> user = jdbcTemplate.queryForMap(
        "select id, senha_hash, ativo from usuarios where username=?",
        username
    );

    boolean active = Boolean.TRUE.equals(user.get("ativo"));
    String hash = (String) user.get("senha_hash");
    if (!active || !passwordEncoder.matches(password, hash)) {
      UUID userId = (UUID) user.get("id");
      logLogin(userId, false, request, "credenciais invalidas");
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciais invalidas");
    }

    List<String> roles = jdbcTemplate.queryForList(
        """
            select distinct p.nome
            from perfis p
            join usuarios_perfis up on up.perfil_id = p.id
            where up.usuario_id = ?
            """,
        String.class,
        user.get("id")
    );

    UUID userId = (UUID) user.get("id");
    String token = jwtService.createToken(userId.toString(), roles);

    jdbcTemplate.update(
        "insert into sessoes (usuario_id, jwt_id, expira_em, ip, user_agent) values (?,?,?,?,?)",
        user.get("id"),
        Integer.toHexString(token.hashCode()),
        Timestamp.from(Instant.now().plus(60, ChronoUnit.MINUTES)),
        request != null ? request.getRemoteAddr() : null,
        request != null ? request.getHeader("User-Agent") : null
    );

    logLogin(userId, true, request, "ok");
    return token;
  }

  public void changePassword(String oldPassword, String newPassword) {
    String subject = (String) SecurityContextHolder.getContext()
        .getAuthentication()
        .getPrincipal();
    UUID userId = UUID.fromString(subject);

    Map<String, Object> user = jdbcTemplate.queryForMap(
        "select senha_hash from usuarios where id=?",
        userId
    );
    String hash = (String) user.get("senha_hash");
    if (!passwordEncoder.matches(oldPassword, hash)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Senha atual incorreta");
    }

    jdbcTemplate.update(
        "update usuarios set senha_hash=? where id=?",
        passwordEncoder.encode(newPassword),
        userId
    );
  }

  public void logout(String authorizationHeader) {
    if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
      return;
    }
    String token = authorizationHeader.substring(7);
    jdbcTemplate.update(
        "update sessoes set revogado = true where jwt_id = ?",
        Integer.toHexString(token.hashCode())
    );
  }

  private void logLogin(UUID userId, boolean success, HttpServletRequest request, String message) {
    jdbcTemplate.update(
        "insert into logs_login (usuario_id, sucesso, ip, user_agent, mensagem) values (?,?,?,?,?)",
        userId,
        success,
        request != null ? request.getRemoteAddr() : null,
        request != null ? request.getHeader("User-Agent") : null,
        message
    );
  }
}
