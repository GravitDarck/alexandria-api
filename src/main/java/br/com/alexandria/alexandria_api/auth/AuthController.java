package br.com.alexandria.alexandria_api.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;

  public record LoginReq(@NotBlank String username, @NotBlank String password) {}

  public record TokenRes(String accessToken) {}

  @PostMapping("/login")
  public TokenRes login(@RequestBody @Valid LoginReq req, HttpServletRequest request) {
    String token = authService.login(req.username(), req.password(), request);
    return new TokenRes(token);
  }

  public record ChangePassReq(@NotBlank String oldPassword, @NotBlank String newPassword) {}

  @PostMapping("/change-password")
  public void changePassword(@RequestBody @Valid ChangePassReq req) {
    authService.changePassword(req.oldPassword(), req.newPassword());
  }

  @PostMapping("/logout")
  public void logout(@RequestHeader(name = "Authorization", required = false) String authorizationHeader) {
    authService.logout(authorizationHeader);
  }
}
