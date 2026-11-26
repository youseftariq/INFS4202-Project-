package com.halcyon.uniplace.security;

import com.halcyon.uniplace.domain.Role;
import com.halcyon.uniplace.domain.User;
import com.halcyon.uniplace.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TC-SEC-017 / USR-03: an account is locked for 15 minutes after 5 failed logins.
 * White-box tests of LoginAttemptService (boundary values 4 / 5 / 6 failures).
 */
class LoginAttemptServiceTest extends IntegrationTest {

    @Autowired LoginAttemptService attempts;

    private User newUser(String name) {
        return users.save(new User(name, "{noop}unused", Role.COORDINATOR, name, null));
    }

    private User reload(String name) {
        return users.findByUsername(name).orElseThrow();
    }

    private void fail(String name, int times) {
        for (int i = 0; i < times; i++) {
            attempts.failed(name);
        }
    }

    @Test
    @DisplayName("TC-SEC-017 / USR-03: 4 failed logins do not lock the account")
    void fourFailuresDoNotLock() {
        newUser("locker");
        fail("locker", 4);
        assertNull(reload("locker").getLockedUntil());
        assertEquals(4, reload("locker").getFailedAttempts());
    }

    @Test
    @DisplayName("TC-SEC-017 / USR-03: the 5th failed login locks the account for 15 minutes")
    void fifthFailureLocksForFifteenMinutes() {
        newUser("locker");
        LocalDateTime before = LocalDateTime.now(clock);
        fail("locker", 5);
        LocalDateTime lockedUntil = reload("locker").getLockedUntil();
        assertNotNull(lockedUntil);
        assertTrue(lockedUntil.isAfter(before.plusMinutes(LoginAttemptService.LOCK_MINUTES - 1)));
        assertTrue(lockedUntil.isBefore(LocalDateTime.now(clock).plusMinutes(LoginAttemptService.LOCK_MINUTES + 1)));
        assertEquals(0, reload("locker").getFailedAttempts());
    }

    @Test
    @DisplayName("TC-SEC-017 / USR-03: a 6th failure while locked does not extend the lock")
    void failureWhileLockedChangesNothing() {
        newUser("locker");
        fail("locker", 5);
        LocalDateTime lockedUntil = reload("locker").getLockedUntil();
        fail("locker", 1);
        assertEquals(lockedUntil, reload("locker").getLockedUntil());
        assertEquals(0, reload("locker").getFailedAttempts());
    }

    @Test
    @DisplayName("TC-SEC-017 / USR-03: a successful login resets the failure counter")
    void successResetsTheCounter() {
        newUser("locker");
        fail("locker", 4);
        attempts.succeeded("locker");
        assertEquals(0, reload("locker").getFailedAttempts());
        fail("locker", 4);
        assertNull(reload("locker").getLockedUntil());
    }

    @Test
    @DisplayName("TC-SEC-017 / USR-03: failures are counted again once the lock has expired")
    void failuresCountAgainAfterTheLockExpires() {
        User u = newUser("locker");
        u.setLockedUntil(LocalDateTime.now(clock).minusMinutes(1));
        users.save(u);
        fail("locker", 1);
        assertEquals(1, reload("locker").getFailedAttempts());
    }

    @Test
    @DisplayName("TC-SEC-017 / USR-03: a failed login for an unknown user name is ignored without an error")
    void unknownUserIsIgnored() {
        assertDoesNotThrow(() -> attempts.failed("nobody"));
        assertFalse(users.findByUsername("nobody").isPresent());
    }
}
