package com.halcyon.uniplace.service;

import com.halcyon.uniplace.domain.Application;
import com.halcyon.uniplace.domain.ApplicationState;
import com.halcyon.uniplace.domain.Company;
import com.halcyon.uniplace.domain.CompanyStatus;
import com.halcyon.uniplace.domain.Posting;
import com.halcyon.uniplace.domain.Student;
import com.halcyon.uniplace.domain.User;
import com.halcyon.uniplace.engine.EngineClient;
import com.halcyon.uniplace.engine.EngineDtos.EligibilityRequest;
import com.halcyon.uniplace.engine.EngineDtos.EligibilityResult;
import com.halcyon.uniplace.engine.EngineDtos.MatchResult;
import com.halcyon.uniplace.engine.EngineDtos.RankRequest;
import com.halcyon.uniplace.engine.EngineDtos.RankResult;
import com.halcyon.uniplace.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * White-box and state-based tests of ApplicationService and RankingService against APP-01 to APP-09, STU-03 and
 * COM-02. Designed in docs/test-cases.md as TC-APP-001 to TC-APP-017 and TC-APP-026. The Engine is stubbed, so
 * these tests check the Portal's own rules; the Engine itself is tested in tests/api (ApplicationsApiTest).
 * Limits and the shortlist threshold are read from SettingsService, so the tests follow the configured values
 * (defaults: GOOD 5, PROBATION 2, threshold 60).
 */
class ApplicationRulesTest extends IntegrationTest {

    /** 300 characters without double spaces; cut to any length by {@link #letter(int)}. */
    private static final String TEXT = ("I am applying for this internship because it matches my studies in information "
            + "systems and the projects I completed with databases, web development and testing during my degree. "
            + "I would like to learn from your team and contribute to real work from the first week of the placement. ")
            .repeat(3);

    @Autowired ApplicationService service;
    @Autowired RankingService ranking;
    @Autowired SettingsService settings;
    @MockBean EngineClient engine;

    Company company;
    Posting posting;
    Student student;
    User studentUser;
    User recruiter;

    @BeforeEach
    void setUp() {
        company = company("QTech", CompanyStatus.APPROVED);
        posting = openPosting(company, "Backend Intern", List.of("Java", "SQL"), 2);
        student = student("3.20", List.of("Java", "SQL"));
        studentUser = studentUser(student);
        recruiter = recruiter(company);
        validDocuments(student);
        when(engine.checkEligibility(any())).thenReturn(EligibilityResult.ok());
        when(engine.match(any())).thenReturn(new MatchResult(70, List.of("Java", "SQL"), List.of(), List.of()));
    }

    /** A letter of exactly n characters, with no leading or trailing space (the Portal trims the letter). */
    static String letter(int n) {
        String s = TEXT.substring(0, n - 1) + ".";
        assertEquals(n, s.trim().length(), "test data: letter length");
        return s;
    }

    private Posting newPosting(String title) {
        return openPosting(company, title, List.of("Java"), 1);
    }

    private ApplicationState stateOf(Application a) {
        return applications.findById(a.getId()).orElseThrow().getState();
    }

    // ------------------------------------------------------------------ APP-01: letter length and deadline

    @Test
    @DisplayName("TC-APP-001 / APP-01: a letter of 99 characters is refused with the rule and its length")
    void letterOf99IsRefused() {
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> service.submit(studentUser, posting.getId(), letter(99)));
        assertAll(
                () -> assertTrue(ex.getMessage().contains("between 100 and 2000"), ex.getMessage()),
                () -> assertTrue(ex.getMessage().contains("99"), ex.getMessage()));
    }

    @Test
    @DisplayName("TC-APP-001 / APP-01: a letter of exactly 100 characters is accepted")
    void letterOf100IsAccepted() {
        assertEquals(ApplicationState.SUBMITTED, service.submit(studentUser, posting.getId(), letter(100)).getState());
    }

    @Test
    @DisplayName("TC-APP-002 / APP-01: a letter of exactly 2,000 characters is accepted")
    void letterOf2000IsAccepted() {
        String text = ("x".repeat(99) + " ").repeat(20).trim() + "x";
        assertEquals(2000, text.length(), "test data: letter length");
        assertEquals(ApplicationState.SUBMITTED, service.submit(studentUser, posting.getId(), text).getState());
    }

    @Test
    @DisplayName("TC-APP-002 / APP-01: a letter of 2,001 characters is refused")
    void letterOf2001IsRefused() {
        String text = ("x".repeat(99) + " ").repeat(20).trim() + "xx";
        assertEquals(2001, text.length(), "test data: letter length");
        assertThrows(BusinessRuleException.class, () -> service.submit(studentUser, posting.getId(), text));
    }

    @Test
    @DisplayName("TC-APP-003 / APP-01, POS-04: applications are accepted until the end of the deadline day")
    void applicationOnTheDeadlineDayIsAccepted() {
        posting.setDeadline(today());
        postings.save(posting);

        assertEquals(ApplicationState.SUBMITTED, service.submit(studentUser, posting.getId(), letter(150)).getState());
    }

    @Test
    @DisplayName("TC-APP-003 / APP-01, POS-04: an application the day after the deadline is refused")
    void applicationAfterTheDeadlineIsRefused() {
        posting.setDeadline(today().minusDays(1));
        postings.save(posting);

        assertThrows(BusinessRuleException.class, () -> service.submit(studentUser, posting.getId(), letter(150)));
    }

    // ------------------------------------------------------------------ APP-02: active-application limits

    @Test
    @DisplayName("TC-APP-004 / APP-02: a GOOD student can hold the maximum number of active applications, not one more")
    void goodStandingLimitBoundary() {
        int max = settings.maxActiveGood();
        for (int i = 1; i < max; i++) {
            application(student, newPosting("Earlier " + i), ApplicationState.SUBMITTED, 50);
        }

        Application last = service.submit(studentUser, posting.getId(), letter(150));
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> service.submit(studentUser, newPosting("One too many").getId(), letter(150)));

        assertAll(
                () -> assertEquals(ApplicationState.SUBMITTED, last.getState()),
                () -> assertTrue(ex.getMessage().contains(String.valueOf(max)), ex.getMessage()));
    }

    @Test
    @DisplayName("TC-APP-005 / APP-02, STU-03: a PROBATION student's limit is the PROBATION setting")
    void probationStudentUsesTheProbationLimit() {
        Student weak = student("1.80", List.of("Excel"));

        assertEquals(settings.maxActiveProbation(), service.activeLimitFor(weak),
                "GPA 1.80 is PROBATION standing (STU-03), so the PROBATION limit applies (APP-02)");
    }

    @Test
    @DisplayName("TC-APP-005 / APP-02: a PROBATION student at the PROBATION limit cannot submit another application")
    void probationStudentAboveTheLimitIsRefused() {
        Student weak = student("1.80", List.of("Java"));
        User weakUser = studentUser(weak);
        validDocuments(weak);
        for (int i = 1; i <= settings.maxActiveProbation(); i++) {
            application(weak, newPosting("Probation " + i), ApplicationState.SUBMITTED, 50);
        }

        assertThrows(BusinessRuleException.class, () -> service.submit(weakUser, posting.getId(), letter(150)));
    }

    @Test
    @DisplayName("TC-APP-006 / APP-02: WITHDRAWN applications do not count as active")
    void withdrawnApplicationsAreNotActive() {
        for (int i = 0; i < 4; i++) {
            application(student, newPosting("Withdrawn " + i), ApplicationState.WITHDRAWN, 40);
        }
        application(student, newPosting("Current"), ApplicationState.SUBMITTED, 40);

        assertEquals(1, service.countActive(student),
                "Active means SUBMITTED, SHORTLISTED, INTERVIEW or OFFERED (APP-02)");
    }

    // ------------------------------------------------------------------ APP-03: applying again

    @Test
    @DisplayName("TC-APP-007 / APP-03: a student may apply again after a REJECTED application while the posting is open")
    void studentCanApplyAgainAfterRejection() {
        application(student, posting, ApplicationState.REJECTED, 40);

        assertEquals(ApplicationState.SUBMITTED, service.submit(studentUser, posting.getId(), letter(150)).getState());
    }

    @Test
    @DisplayName("TC-APP-007 / APP-03: a student may apply again after a WITHDRAWN application while the posting is open")
    void studentCanApplyAgainAfterWithdrawal() {
        application(student, posting, ApplicationState.WITHDRAWN, 40);

        assertEquals(ApplicationState.SUBMITTED, service.submit(studentUser, posting.getId(), letter(150)).getState());
    }

    // ------------------------------------------------------------------ APP-04: what is sent to the Engine

    @Test
    @DisplayName("TC-APP-010 / APP-04, POS-01: the eligibility check uses the posting's minimum GPA and the prior internships")
    void eligibilityRequestUsesThePostingAndTheStudent() {
        posting.setMinGpa(new BigDecimal("3.00"));
        postings.save(posting);
        student.setPriorInternships(2);
        students.save(student);
        ArgumentCaptor<EligibilityRequest> sent = ArgumentCaptor.forClass(EligibilityRequest.class);

        service.submit(studentUser, posting.getId(), letter(150));

        verify(engine).checkEligibility(sent.capture());
        assertAll(
                () -> assertEquals(0, new BigDecimal("3.00").compareTo(sent.getValue().minGpa),
                        "minGpa sent: " + sent.getValue().minGpa),
                () -> assertEquals(2, sent.getValue().priorInternships, "priorInternships sent"));
    }

    // ------------------------------------------------------------------ APP-06, APP-08: withdrawing

    @ParameterizedTest(name = "from {0}")
    @EnumSource(value = ApplicationState.class, names = {"ACCEPTED", "REJECTED", "WITHDRAWN"})
    @DisplayName("TC-APP-014 / APP-06, APP-08: an application in a final state cannot be withdrawn (#38)")
    void finalApplicationCannotBeWithdrawn(ApplicationState finalState) {
        Application a = application(student, posting, finalState, 70);

        assertThrows(BusinessRuleException.class, () -> service.withdraw(studentUser, a.getId()));
        assertEquals(finalState, stateOf(a));
    }

    // ------------------------------------------------------------------ APP-07: shortlist threshold

    @Test
    @DisplayName("TC-APP-015 / APP-07: a score equal to the threshold is shortlisted without a justification")
    void scoreAtTheThresholdNeedsNoJustification() {
        Application a = application(student, posting, ApplicationState.SUBMITTED, settings.shortlistThreshold());

        service.shortlist(recruiter, a.getId(), null);

        assertEquals(ApplicationState.SHORTLISTED, stateOf(a));
    }

    @Test
    @DisplayName("TC-APP-015 / APP-07: a score one below the threshold needs a justification")
    void scoreJustBelowTheThresholdNeedsJustification() {
        Application a = application(student, posting, ApplicationState.SUBMITTED, settings.shortlistThreshold() - 1);

        assertThrows(BusinessRuleException.class, () -> service.shortlist(recruiter, a.getId(), null));
        assertEquals(ApplicationState.SUBMITTED, stateOf(a));
    }

    @Test
    @DisplayName("TC-APP-015 / APP-07: a score one above the threshold is shortlisted without a justification")
    void scoreJustAboveTheThresholdNeedsNoJustification() {
        Application a = application(student, posting, ApplicationState.SUBMITTED, settings.shortlistThreshold() + 1);

        service.shortlist(recruiter, a.getId(), null);

        assertEquals(ApplicationState.SHORTLISTED, stateOf(a));
    }

    // ------------------------------------------------------------------ COM-02: frozen applications

    @ParameterizedTest(name = "company {0}")
    @EnumSource(value = CompanyStatus.class, names = {"PENDING", "SUSPENDED"})
    @DisplayName("TC-APP-017 / COM-02: applications of a company that is not APPROVED are frozen")
    void applicationsOfANonApprovedCompanyAreFrozen(CompanyStatus status) {
        Application toShortlist = application(student, posting, ApplicationState.SUBMITTED, 90);
        Application toWithdraw = application(student, newPosting("Second"), ApplicationState.SUBMITTED, 90);
        company.setStatus(status);
        companies.save(company);

        assertAll(
                () -> assertThrows(BusinessRuleException.class,
                        () -> service.shortlist(recruiter, toShortlist.getId(), null), "shortlist"),
                () -> assertThrows(BusinessRuleException.class,
                        () -> service.withdraw(studentUser, toWithdraw.getId()), "withdraw"),
                () -> assertEquals(ApplicationState.SUBMITTED, stateOf(toShortlist)),
                () -> assertEquals(ApplicationState.SUBMITTED, stateOf(toWithdraw)));
    }

    // ------------------------------------------------------------------ APP-09: ranking

    @Test
    @DisplayName("TC-APP-026 / APP-09: the ranking asks the Engine for as many places as the seats still free")
    void rankingUsesTheSeatsStillFree() {
        Posting p = openPosting(company, "Data Intern", List.of("SQL"), 3);
        p.setSeatsTaken(1);
        postings.save(p);
        Application a1 = application(student("3.00", List.of("SQL")), p, ApplicationState.SUBMITTED, 80);
        Application a2 = application(student("3.10", List.of("SQL")), p, ApplicationState.SHORTLISTED, 60);
        when(engine.rank(any())).thenReturn(new RankResult(List.of(a1.getId(), a2.getId()), List.of(a1.getId())));
        ArgumentCaptor<RankRequest> sent = ArgumentCaptor.forClass(RankRequest.class);

        ranking.rank(p);

        verify(engine).rank(sent.capture());
        assertEquals(2, sent.getValue().seats, "3 seats, 1 already taken");
    }
}
