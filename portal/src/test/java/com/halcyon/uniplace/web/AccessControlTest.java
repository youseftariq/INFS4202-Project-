package com.halcyon.uniplace.web;

import com.halcyon.uniplace.domain.Application;
import com.halcyon.uniplace.domain.ApplicationState;
import com.halcyon.uniplace.domain.Company;
import com.halcyon.uniplace.domain.CompanyStatus;
import com.halcyon.uniplace.domain.DocumentType;
import com.halcyon.uniplace.domain.Posting;
import com.halcyon.uniplace.domain.Role;
import com.halcyon.uniplace.domain.Student;
import com.halcyon.uniplace.domain.User;
import com.halcyon.uniplace.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests of access control and output escaping (module Users & Access / Security).
 *
 * Tests tagged "defect" are EXPECTED TO FAIL while the matching defect is open: their failure is the evidence.
 * Run only them:            .\mvnw.cmd test "-Dgroups=defect" "-Dmaven.test.failure.ignore=true"
 * Run everything except:    .\mvnw.cmd test "-DexcludedGroups=defect"
 */
@AutoConfigureMockMvc
class AccessControlTest extends IntegrationTest {

    @Autowired MockMvc mvc;

    private Company acme;
    private Company other;
    private Posting acmePosting;
    private Student alice;
    private Student bob;
    private User aliceUser;
    private User bobUser;
    private User acmeRecruiter;
    private User otherRecruiter;
    private User coordinator;
    private Application aliceApplication;

    @BeforeEach
    void createData() {
        acme = company("Acme", CompanyStatus.APPROVED);
        other = company("Other Ltd", CompanyStatus.APPROVED);
        acmePosting = openPosting(acme, "Backend Intern", List.of("Java"), 2);
        alice = student("3.20", List.of("Java"));
        bob = student("3.00", List.of("Java"));
        aliceUser = studentUser(alice);
        bobUser = studentUser(bob);
        acmeRecruiter = recruiter(acme);
        otherRecruiter = recruiter(other);
        coordinator = users.save(new User("coord", "{noop}unused", Role.COORDINATOR, "Coord", null));
        validDocuments(alice);
        aliceApplication = application(alice, acmePosting, ApplicationState.SUBMITTED, 80);
    }

    private static RequestPostProcessor as(User u) {
        return user(u.getUsername()).roles(u.getRole().name());
    }

    // ---------------------------------------------------------------- TC-SEC-008 / TC-SEC-009 (application page)

    @Test
    @DisplayName("TC-SEC-008 control / USR-05: a student can open their own application")
    void studentOpensOwnApplication() throws Exception {
        mvc.perform(get("/applications/" + aliceApplication.getId()).with(as(aliceUser)))
                .andExpect(status().isOk());
    }

    @Test
    @Tag("defect")
    @DisplayName("TC-SEC-008 / USR-05: a student cannot open another student's application (DEFECT)")
    void studentCannotOpenAnotherStudentsApplication() throws Exception {
        mvc.perform(get("/applications/" + aliceApplication.getId()).with(as(bobUser)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Tag("defect")
    @DisplayName("TC-SEC-009 / USR-05: a recruiter of another company cannot open the application (DEFECT)")
    void recruiterOfAnotherCompanyCannotOpenApplication() throws Exception {
        mvc.perform(get("/applications/" + aliceApplication.getId()).with(as(otherRecruiter)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TC-SEC-009 control / USR-05: a recruiter of the posting's company can open the application")
    void recruiterOfTheCompanyOpensApplication() throws Exception {
        mvc.perform(get("/applications/" + aliceApplication.getId()).with(as(acmeRecruiter)))
                .andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- TC-SEC-010 (direct URLs)

    @ParameterizedTest(name = "TC-SEC-010 / USR-05, SEC-05: a student cannot open {0}")
    @ValueSource(strings = {"/coordinator/settings", "/coordinator/users", "/coordinator/students",
            "/coordinator/audit", "/coordinator/outbox", "/coordinator/tools", "/recruiter/postings"})
    void studentCannotOpenStaffPages(String path) throws Exception {
        mvc.perform(get(path).with(as(aliceUser))).andExpect(status().isForbidden());
    }

    @Test
    @Tag("defect")
    @DisplayName("TC-SEC-010 / USR-05, SEC-05: a student cannot open /coordinator/reports (DEFECT)")
    void studentCannotOpenReports() throws Exception {
        mvc.perform(get("/coordinator/reports").with(as(aliceUser))).andExpect(status().isForbidden());
    }

    @Test
    @Tag("defect")
    @DisplayName("TC-SEC-010 / USR-05, SEC-05: a recruiter cannot open /coordinator/reports (DEFECT)")
    void recruiterCannotOpenReports() throws Exception {
        mvc.perform(get("/coordinator/reports").with(as(acmeRecruiter))).andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- TC-SEC-011 (documents)

    @Test
    @DisplayName("TC-SEC-011 / USR-05: another student cannot download a student's document")
    void studentCannotDownloadAnotherStudentsDocument() throws Exception {
        Long id = documents.findByStudentAndType(alice, DocumentType.CV).orElseThrow().getId();
        mvc.perform(get("/documents/" + id).with(as(bobUser))).andExpect(status().isForbidden());
    }

    @Test
    @Tag("defect")
    @DisplayName("TC-SEC-011 / USR-05: a recruiter of a company the student never applied to cannot download the document (DEFECT)")
    void unrelatedRecruiterCannotDownloadDocument() throws Exception {
        Long id = documents.findByStudentAndType(alice, DocumentType.CV).orElseThrow().getId();
        // Today the handler lets every recruiter through and then answers 404 because the file is not on disk
        // in the test; after the fix it must answer 403 before looking for the file.
        mvc.perform(get("/documents/" + id).with(as(otherRecruiter))).andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- TC-SEC-012 (CSRF)

    @Test
    @DisplayName("TC-SEC-012 control / SEC-02: a POST to the change-password form without a CSRF token is refused")
    void passwordFormNeedsACsrfToken() throws Exception {
        mvc.perform(post("/account/password").with(as(aliceUser)).param("csrf_test", "1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Tag("defect")
    @DisplayName("TC-SEC-012 / SEC-02: a POST to the Coordinator settings without a CSRF token is refused (DEFECT)")
    void settingsFormNeedsACsrfToken() throws Exception {
        mvc.perform(post("/coordinator/settings").with(as(coordinator)).param("csrf_test", "1"))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- TC-SEC-014 (output escaping)

    @Test
    @Tag("defect")
    @DisplayName("TC-SEC-014 / SEC-07: HTML in a company name is escaped on the postings list (DEFECT)")
    void companyNameIsEscapedOnThePostingsList() throws Exception {
        Company evil = new Company("Evil <i>x</i> Ltd", "Technology", "hr@evil.qa");
        evil.setStatus(CompanyStatus.APPROVED);
        evil = companies.save(evil);
        openPosting(evil, "Escaping Intern", List.of("Java"), 1);

        mvc.perform(get("/postings").with(as(aliceUser)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("<i>x</i>"))));
    }
}
