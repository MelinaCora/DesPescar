package com.despescar.identityservice.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Usuario del sistema. Cada usuario puede tener mas de un rol y cada rol puede
 * estar asociado a un alcance especifico (por ejemplo, una aerolinea o un hotel).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private LocalDate registrationDate = LocalDate.now();

    @Column(nullable = false)
    private Boolean isActive = true;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts = 0;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<UserRole> roles = new LinkedHashSet<>();

    public void addRole(UserRole userRole) {
        roles.add(userRole);
        userRole.setUser(this);
    }

    public void removeRole(UserRole userRole) {
        roles.remove(userRole);
        userRole.setUser(null);
    }

    public String getPrimaryRoleName() {
        if (roles.stream().anyMatch(userRole -> userRole.getRole() != null
                && "SUPER_ADMIN".equalsIgnoreCase(userRole.getRole().getName()))) {
            return "SUPER_ADMIN";
        }
        if (roles.stream().anyMatch(userRole -> userRole.getRole() != null
                && "AIRLINE_ADMIN".equalsIgnoreCase(userRole.getRole().getName()))) {
            return "AIRLINE_ADMIN";
        }
        if (roles.stream().anyMatch(userRole -> userRole.getRole() != null
                && "HOTEL_ADMIN".equalsIgnoreCase(userRole.getRole().getName()))) {
            return "HOTEL_ADMIN";
        }
        return "USER";
    }
}
