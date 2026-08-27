package com.despescar.identityservice.config;

import com.despescar.identityservice.entity.Permission;
import com.despescar.identityservice.entity.Role;
import com.despescar.identityservice.repository.PermissionRepository;
import com.despescar.identityservice.repository.RoleRepository;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Inicializa roles y permisos basicos del sistema al arrancar la aplicacion.
 *
 * <p>La idea es evitar depender de registros manuales en base de datos y dejar
 * el modelo de seguridad listo para cada ambiente.</p>
 */
@Component
public class DataInitializer {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    public DataInitializer(RoleRepository roleRepository, PermissionRepository permissionRepository) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
    }

    @PostConstruct
    public void init() {
        Permission viewFlights = getOrCreatePermission("VIEW_FLIGHTS", "Puede consultar vuelos");
        Permission viewHotels = getOrCreatePermission("VIEW_HOTELS", "Puede consultar hoteles");
        Permission selfBooking = getOrCreatePermission("SELF_BOOKING", "Puede crear y gestionar sus reservas propias");

        Permission manageFlights = getOrCreatePermission("MANAGE_FLIGHTS", "Puede administrar vuelos de una aerolinea");
        Permission manageHotels = getOrCreatePermission("MANAGE_HOTELS", "Puede administrar hoteles asignados");
        Permission manageUsers = getOrCreatePermission("MANAGE_USERS", "Puede administrar usuarios del sistema");
        Permission manageReservations = getOrCreatePermission("MANAGE_RESERVATIONS", "Puede administrar reservas del sistema");
        Permission readReports = getOrCreatePermission("READ_REPORTS", "Puede consultar reportes y estadisticas");

        createRoleIfMissing("USER", "Usuario basico del sistema", Set.of(viewFlights, viewHotels, selfBooking));
        createRoleIfMissing("SUPER_ADMIN", "Administrador global del sistema", Set.of(
                manageUsers,
                manageFlights,
                manageHotels,
                manageReservations,
                readReports
        ));
        createRoleIfMissing("AIRLINE_ADMIN", "Administrador de aerolineas", Set.of(
                manageFlights,
                manageReservations,
                readReports
        ));
        createRoleIfMissing("HOTEL_ADMIN", "Administrador de hoteles", Set.of(
                manageHotels,
                manageReservations,
                readReports
        ));
    }

    private Permission getOrCreatePermission(String name, String description) {
        return permissionRepository.findByName(name)
                .orElseGet(() -> {
                    Permission permission = new Permission();
                    permission.setName(name);
                    permission.setDescription(description);
                    return permissionRepository.save(permission);
                });
    }

    private void createRoleIfMissing(String roleName, String description, Set<Permission> permissions) {
        roleRepository.findByNameIgnoreCase(roleName).ifPresentOrElse(
                role -> {
                    role.setDescription(description);
                    role.setPermissions(permissions);
                    roleRepository.save(role);
                },
                () -> {
                    Role role = new Role();
                    role.setName(roleName);
                    role.setDescription(description);
                    role.setPermissions(permissions);
                    roleRepository.save(role);
                }
        );
    }
}
