package com.despescar.identityservice.service;

import com.despescar.identityservice.dto.response.LoginResponse;
import com.despescar.identityservice.entity.Role;
import com.despescar.identityservice.entity.User;
import com.despescar.identityservice.entity.UserRole;
import com.despescar.identityservice.exception.GoogleLoginException;
import com.despescar.identityservice.exception.RoleNotFoundException;
import com.despescar.identityservice.google.GoogleIdentity;
import com.despescar.identityservice.google.GoogleTokenVerifier;
import com.despescar.identityservice.repository.RoleRepository;
import com.despescar.identityservice.repository.UserRepository;
import com.despescar.identityservice.security.JwtService;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.Base64;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ingreso con Google: con el ID token verificado se busca el usuario por correo y, si no existe,
 * se crea con rol USER y una contraseña aleatoria (solo puede entrar con Google hasta que la cambie).
 */
@Service
public class GoogleAuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final GoogleTokenVerifier verifier;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public GoogleAuthService(
            GoogleTokenVerifier verifier,
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenService refreshTokenService
    ) {
        this.verifier = verifier;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public LoginResponse login(String credential) {
        GoogleIdentity identity = verifier.verify(credential);
        User user = userRepository.findByEmail(identity.email())
                .orElseGet(() -> crearUsuario(identity));

        if (Boolean.FALSE.equals(user.getIsActive())) {
            throw new GoogleLoginException("Tu cuenta está desactivada.");
        }

        String accessToken = jwtService.generateToken(user.getId(), user.getEmail(), user.getPrimaryRoleName());
        String refreshToken = refreshTokenService.createRefreshToken(user);
        return new LoginResponse(accessToken, refreshToken, "Bearer", jwtService.getAccessTokenExpirationSeconds());
    }

    private User crearUsuario(GoogleIdentity identity) {
        Role defaultRole = roleRepository.findByNameIgnoreCase("USER")
                .orElseThrow(RoleNotFoundException::new);

        User user = new User();
        user.setFirstName(identity.firstName());
        user.setLastName(identity.lastName());
        user.setEmail(identity.email());
        user.setPassword(passwordEncoder.encode(contrasenaAleatoria()));
        user.setRegistrationDate(LocalDate.now());
        user.setIsActive(true);

        UserRole userRole = new UserRole();
        userRole.setRole(defaultRole);
        user.addRole(userRole);
        return userRepository.save(user);
    }

    private static String contrasenaAleatoria() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
