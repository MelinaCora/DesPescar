package com.despescar.identityservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.despescar.identityservice.dto.response.LoginResponse;
import com.despescar.identityservice.entity.Role;
import com.despescar.identityservice.entity.User;
import com.despescar.identityservice.exception.GoogleLoginException;
import com.despescar.identityservice.google.GoogleIdentity;
import com.despescar.identityservice.google.GoogleTokenVerifier;
import com.despescar.identityservice.repository.RoleRepository;
import com.despescar.identityservice.repository.UserRepository;
import com.despescar.identityservice.security.JwtService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class GoogleAuthServiceTest {

    @Mock private GoogleTokenVerifier verifier;
    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private RefreshTokenService refreshTokenService;

    @InjectMocks private GoogleAuthService service;

    private final GoogleIdentity identity = new GoogleIdentity("ana@gmail.com", "Ana", "Pérez");

    @BeforeEach
    void setUp() {
        when(verifier.verify("tok")).thenReturn(identity);
    }

    @Test
    void laPrimeraVezCreaElUsuarioConRolUserYContrasenaAleatoria() {
        Role user = new Role();
        user.setName("USER");
        when(userRepository.findByEmail("ana@gmail.com")).thenReturn(Optional.empty());
        when(roleRepository.findByNameIgnoreCase("USER")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(7L);
            return u;
        });
        when(jwtService.generateToken(7L, "ana@gmail.com", "USER")).thenReturn("access");
        when(refreshTokenService.createRefreshToken(any(User.class))).thenReturn("refresh");
        when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);

        LoginResponse response = service.login("tok");

        ArgumentCaptor<User> guardado = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(guardado.capture());
        assertThat(guardado.getValue().getFirstName()).isEqualTo("Ana");
        assertThat(guardado.getValue().getLastName()).isEqualTo("Pérez");
        assertThat(guardado.getValue().getPassword()).isEqualTo("hash");
        assertThat(guardado.getValue().getPrimaryRoleName()).isEqualTo("USER");
        assertThat(response.getAccessToken()).isEqualTo("access");
        assertThat(response.getRefreshToken()).isEqualTo("refresh");
    }

    @Test
    void siElCorreoYaExisteNoCreaOtroUsuario() {
        User existente = new User();
        existente.setId(3L);
        existente.setEmail("ana@gmail.com");
        when(userRepository.findByEmail("ana@gmail.com")).thenReturn(Optional.of(existente));
        when(jwtService.generateToken(3L, "ana@gmail.com", "USER")).thenReturn("access");
        when(refreshTokenService.createRefreshToken(existente)).thenReturn("refresh");

        service.login("tok");

        verify(userRepository, never()).save(any());
    }

    @Test
    void unaCuentaDesactivadaNoEntra() {
        User existente = new User();
        existente.setIsActive(false);
        when(userRepository.findByEmail("ana@gmail.com")).thenReturn(Optional.of(existente));

        assertThatThrownBy(() -> service.login("tok")).isInstanceOf(GoogleLoginException.class);
    }
}
