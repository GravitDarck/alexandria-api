package br.com.alexandria.alexandria_api.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import javax.crypto.SecretKey;

@Service
public class JwtService {

  private final SecretKey signingKey;
  private final String issuer;
  private final long accessTokenMinutes;

  public JwtService(@Value("${app.jwt.secret}") String secret,
                    @Value("${app.jwt.issuer}") String issuer,
                    @Value("${app.jwt.access-token-minutes}") long accessTokenMinutes) {
    this.signingKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret));
    this.issuer = issuer;
    this.accessTokenMinutes = accessTokenMinutes;
  }

  public String createToken(String subject, Collection<String> roles) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(subject)
        .issuer(issuer)
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plusSeconds(accessTokenMinutes * 60)))
        .claim("roles", roles)
        .signWith(signingKey)
        .compact();
  }

  public Jws<Claims> parse(String token) {
    return Jwts.parser()
        .verifyWith(signingKey)
        .build()
        .parseSignedClaims(token);
  }
}
