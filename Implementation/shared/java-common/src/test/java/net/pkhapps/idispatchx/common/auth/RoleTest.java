package net.pkhapps.idispatchx.common.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class RoleTest {

    @ParameterizedTest
    @EnumSource(Role.class)
    void fromClaimValue_findsAllRoles(Role role) {
        var found = Role.fromClaimValue(role.getClaimValue());
        assertTrue(found.isPresent());
        assertEquals(role, found.get());
    }

    @Test
    void fromClaimValue_caseInsensitive() {
        assertEquals(Role.DISPATCHER, Role.fromClaimValue("dispatcher").orElse(null));
        assertEquals(Role.DISPATCHER, Role.fromClaimValue("DISPATCHER").orElse(null));
        assertEquals(Role.ADMIN, Role.fromClaimValue("admin").orElse(null));
        assertEquals(Role.OBSERVER, Role.fromClaimValue("OBSERVER").orElse(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Unknown", "SUPERUSER", "root"})
    void fromClaimValue_returnsEmptyForUnknownRoles(String claimValue) {
        var result = Role.fromClaimValue(claimValue);
        assertTrue(result.isEmpty());
    }

    @Test
    void getClaimValue_returnsExpectedValues() {
        assertEquals("Dispatcher", Role.DISPATCHER.getClaimValue());
        assertEquals("Observer", Role.OBSERVER.getClaimValue());
        assertEquals("Admin", Role.ADMIN.getClaimValue());
        assertEquals("Station", Role.STATION.getClaimValue());
        assertEquals("Unit", Role.UNIT.getClaimValue());
    }

    @Test
    void allRolesHaveUniqueClaimValues() {
        var claimValues = java.util.Arrays.stream(Role.values())
                .map(Role::getClaimValue)
                .toList();
        var uniqueClaimValues = new java.util.HashSet<>(claimValues);
        assertEquals(claimValues.size(), uniqueClaimValues.size(), "All roles should have unique claim values");
    }
}
