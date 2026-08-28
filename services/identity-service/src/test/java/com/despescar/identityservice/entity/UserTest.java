package com.despescar.identityservice.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("User Entity Tests")
class UserTest {

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setFirstName("Test");
        user.setLastName("User");
        user.setEmail("test@example.com");
    }

    @Test
    @DisplayName("getPrimaryRoleName should return SUPER_ADMIN when user has that role")
    void testGetPrimaryRoleName_WithSuperAdmin() {
        Role superAdminRole = new Role();
        superAdminRole.setId(1L);
        superAdminRole.setName("SUPER_ADMIN");

        Role userRole = new Role();
        userRole.setId(2L);
        userRole.setName("USER");

        UserRole ur1 = new UserRole();
        ur1.setRole(userRole);
        user.addRole(ur1);

        UserRole ur2 = new UserRole();
        ur2.setRole(superAdminRole);
        user.addRole(ur2);

        assertEquals("SUPER_ADMIN", user.getPrimaryRoleName());
    }

    @Test
    @DisplayName("getPrimaryRoleName should return AIRLINE_ADMIN when no SUPER_ADMIN exists")
    void testGetPrimaryRoleName_WithAirlineAdmin() {
        Role airlineAdminRole = new Role();
        airlineAdminRole.setId(1L);
        airlineAdminRole.setName("AIRLINE_ADMIN");

        Role userRole = new Role();
        userRole.setId(2L);
        userRole.setName("USER");

        UserRole ur1 = new UserRole();
        ur1.setRole(userRole);
        user.addRole(ur1);

        UserRole ur2 = new UserRole();
        ur2.setRole(airlineAdminRole);
        user.addRole(ur2);

        assertEquals("AIRLINE_ADMIN", user.getPrimaryRoleName());
    }

    @Test
    @DisplayName("getPrimaryRoleName should return HOTEL_ADMIN when no higher role exists")
    void testGetPrimaryRoleName_WithHotelAdmin() {
        Role hotelAdminRole = new Role();
        hotelAdminRole.setId(1L);
        hotelAdminRole.setName("HOTEL_ADMIN");

        Role userRole = new Role();
        userRole.setId(2L);
        userRole.setName("USER");

        UserRole ur1 = new UserRole();
        ur1.setRole(userRole);
        user.addRole(ur1);

        UserRole ur2 = new UserRole();
        ur2.setRole(hotelAdminRole);
        user.addRole(ur2);

        assertEquals("HOTEL_ADMIN", user.getPrimaryRoleName());
    }

    @Test
    @DisplayName("getPrimaryRoleName should return USER when only USER role exists")
    void testGetPrimaryRoleName_WithOnlyUser() {
        Role userRole = new Role();
        userRole.setId(1L);
        userRole.setName("USER");

        UserRole ur = new UserRole();
        ur.setRole(userRole);
        user.addRole(ur);

        assertEquals("USER", user.getPrimaryRoleName());
    }

    @Test
    @DisplayName("getPrimaryRoleName should return USER when no roles exist")
    void testGetPrimaryRoleName_WithNoRoles() {
        assertEquals("USER", user.getPrimaryRoleName());
    }

    @Test
    @DisplayName("getPrimaryRoleName should be case insensitive")
    void testGetPrimaryRoleName_CaseInsensitive() {
        Role superAdminRole = new Role();
        superAdminRole.setId(1L);
        superAdminRole.setName("super_admin");

        UserRole ur = new UserRole();
        ur.setRole(superAdminRole);
        user.addRole(ur);

        assertEquals("SUPER_ADMIN", user.getPrimaryRoleName());
    }

    @Test
    @DisplayName("addRole should establish bidirectional relationship")
    void testAddRole() {
        Role role = new Role();
        role.setId(1L);
        role.setName("USER");

        UserRole userRole = new UserRole();
        userRole.setRole(role);

        user.addRole(userRole);

        assertTrue(user.getRoles().contains(userRole));
        assertEquals(user, userRole.getUser());
    }

    @Test
    @DisplayName("removeRole should break bidirectional relationship")
    void testRemoveRole() {
        Role role = new Role();
        role.setId(1L);
        role.setName("USER");

        UserRole userRole = new UserRole();
        userRole.setRole(role);

        user.addRole(userRole);
        user.removeRole(userRole);

        assertFalse(user.getRoles().contains(userRole));
        assertNull(userRole.getUser());
    }
}
