package com.halcyon.uniplace.service;

import com.halcyon.uniplace.domain.Application;
import com.halcyon.uniplace.domain.ApplicationState;
import com.halcyon.uniplace.domain.Company;
import com.halcyon.uniplace.domain.CompanyStatus;
import com.halcyon.uniplace.domain.Posting;
import com.halcyon.uniplace.domain.Role;
import com.halcyon.uniplace.domain.Student;
import com.halcyon.uniplace.domain.User;
import com.halcyon.uniplace.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TC-SEC-019 / USR-05: decision table (user role x record owner) for AccessGuard.
 * The guard itself is correct; the defects found by TC-SEC-008/009/011 are that handlers do not call it.
 */
class AccessGuardTest extends IntegrationTest {

    @Autowired AccessGuard guard;

    private Company acme;
    private Company other;
    private Posting acmePosting;
    private User aliceUser;
    private User bobUser;
    private User acmeRecruiter;
    private User otherRecruiter;
    private User recruiterWithoutCompany;
    private User coordinator;
    private Application aliceApplication;

    @BeforeEach
    void createData() {
        acme = company("Acme", CompanyStatus.APPROVED);
        other = company("Other Ltd", CompanyStatus.APPROVED);
        acmePosting = openPosting(acme, "Backend Intern", List.of("Java"), 2);
        Student alice = student("3.20", List.of("Java"));
        Student bob = student("3.00", List.of("Java"));
        aliceUser = studentUser(alice);
        bobUser = studentUser(bob);
        acmeRecruiter = recruiter(acme);
        otherRecruiter = recruiter(other);
        recruiterWithoutCompany = users.save(new User("rec.nocompany", "{noop}unused", Role.RECRUITER, "No company", null));
        coordinator = users.save(new User("coord", "{noop}unused", Role.COORDINATOR, "Coord", null));
        aliceApplication = application(alice, acmePosting, ApplicationState.SUBMITTED, 80);
    }

    // ---- canView: who may see an application

    @Test
    @DisplayName("TC-SEC-019 / USR-05: a student can view their own application")
    void studentViewsOwnApplication() {
        assertTrue(guard.canView(aliceUser, aliceApplication));
    }

    @Test
    @DisplayName("TC-SEC-019 / USR-05: a student cannot view another student's application")
    void studentCannotViewOthers() {
        assertFalse(guard.canView(bobUser, aliceApplication));
    }

    @Test
    @DisplayName("TC-SEC-019 / USR-05: a recruiter of the posting's company can view the application")
    void recruiterOfTheCompanyViews() {
        assertTrue(guard.canView(acmeRecruiter, aliceApplication));
    }

    @Test
    @DisplayName("TC-SEC-019 / USR-05: a recruiter of another company cannot view the application")
    void recruiterOfAnotherCompanyCannotView() {
        assertFalse(guard.canView(otherRecruiter, aliceApplication));
    }

    @Test
    @DisplayName("TC-SEC-019 / USR-05: a recruiter without a company cannot view any application")
    void recruiterWithoutCompanyCannotView() {
        assertFalse(guard.canView(recruiterWithoutCompany, aliceApplication));
    }

    @Test
    @DisplayName("TC-SEC-019 / USR-05: a coordinator can view every application")
    void coordinatorViewsEverything() {
        assertTrue(guard.canView(coordinator, aliceApplication));
    }

    @Test
    @DisplayName("TC-SEC-019 / USR-05: assertCanView throws ForbiddenException for a foreign student")
    void assertCanViewThrowsForForeignStudent() {
        assertThrows(ForbiddenException.class, () -> guard.assertCanView(bobUser, aliceApplication));
        assertDoesNotThrow(() -> guard.assertCanView(aliceUser, aliceApplication));
    }

    // ---- assertStudentOwns: actions only the owning student may do

    @Test
    @DisplayName("TC-SEC-019 / USR-05: only the owning student passes assertStudentOwns")
    void onlyTheOwnerPassesStudentOwnership() {
        assertDoesNotThrow(() -> guard.assertStudentOwns(aliceUser, aliceApplication));
        assertThrows(ForbiddenException.class, () -> guard.assertStudentOwns(bobUser, aliceApplication));
        assertThrows(ForbiddenException.class, () -> guard.assertStudentOwns(acmeRecruiter, aliceApplication));
        assertThrows(ForbiddenException.class, () -> guard.assertStudentOwns(coordinator, aliceApplication));
    }

    // ---- assertRecruiterOwns: actions only a recruiter of the company may do

    @Test
    @DisplayName("TC-SEC-019 / USR-05: only a recruiter of the company passes assertRecruiterOwns(posting)")
    void onlyTheCompanyRecruiterPassesPostingOwnership() {
        assertDoesNotThrow(() -> guard.assertRecruiterOwns(acmeRecruiter, acmePosting));
        assertThrows(ForbiddenException.class, () -> guard.assertRecruiterOwns(otherRecruiter, acmePosting));
        assertThrows(ForbiddenException.class, () -> guard.assertRecruiterOwns(recruiterWithoutCompany, acmePosting));
        assertThrows(ForbiddenException.class, () -> guard.assertRecruiterOwns(aliceUser, acmePosting));
    }

    @Test
    @DisplayName("TC-SEC-019 / USR-05: assertRecruiterOwns(application) follows the posting's company")
    void applicationOwnershipFollowsThePosting() {
        assertDoesNotThrow(() -> guard.assertRecruiterOwns(acmeRecruiter, aliceApplication));
        assertThrows(ForbiddenException.class, () -> guard.assertRecruiterOwns(otherRecruiter, aliceApplication));
    }
}
