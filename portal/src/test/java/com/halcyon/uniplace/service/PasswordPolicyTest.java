package com.halcyon.uniplace.service;

import com.halcyon.uniplace.domain.Role;
import com.halcyon.uniplace.domain.User;
import com.halcyon.uniplace.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * TC-SEC-018 / USR-02: passwords have at least 10 characters including at least one digit,
 * both when a user is created and when a password is changed.
 */
class PasswordPolicyTest extends IntegrationTest {

    @Autowired UserService service;

    private User user() {
        return service.create("changer", "Password123", Role.COORDINATOR, "Changer", null, null, null);
    }

    @Test
    @DisplayName("TC-SEC-018 / USR-02: creating a user with 9 characters is refused (boundary)")
    void createRefusesNineCharacters() {
        assertThrows(BusinessRuleException.class,
                () -> service.create("nine.chars", "Abcdefgh1", Role.COORDINATOR, "N", null, null, null));
    }

    @Test
    @DisplayName("TC-SEC-018 / USR-02: creating a user with 10 characters and a digit is accepted (boundary)")
    void createAcceptsTenCharactersWithADigit() {
        assertDoesNotThrow(() -> service.create("ten.chars", "Abcdefghi1", Role.COORDINATOR, "T", null, null, null));
    }

    @Test
    @DisplayName("TC-SEC-018 / USR-02: changing a password to 9 characters is refused (boundary)")
    void changeRefusesNineCharacters() {
        User u = user();
        assertThrows(BusinessRuleException.class, () -> service.changePassword(u, "Password123", "Abcdefgh1"));
    }

    @Test
    @DisplayName("TC-SEC-018 / USR-02: changing a password to 10 characters with a digit is accepted (boundary)")
    void changeAcceptsTenCharactersWithADigit() {
        User u = user();
        assertDoesNotThrow(() -> service.changePassword(u, "Password123", "Abcdefghi1"));
    }

    @Test
    @Tag("defect")
    @DisplayName("TC-SEC-018 / USR-02: changing a password to 12 letters without a digit is refused (DEFECT)")
    void changeRefusesPasswordWithoutADigit() {
        User u = user();
        assertThrows(BusinessRuleException.class, () -> service.changePassword(u, "Password123", "abcdefghijkl"));
    }
}
