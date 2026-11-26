package com.halcyon.uniplace.service;

import com.halcyon.uniplace.domain.Application;
import com.halcyon.uniplace.domain.ApplicationState;
import com.halcyon.uniplace.domain.Company;
import com.halcyon.uniplace.domain.CompanyStatus;
import com.halcyon.uniplace.domain.Notification;
import com.halcyon.uniplace.domain.Offer;
import com.halcyon.uniplace.domain.OfferStatus;
import com.halcyon.uniplace.domain.Posting;
import com.halcyon.uniplace.domain.PostingStatus;
import com.halcyon.uniplace.domain.Student;
import com.halcyon.uniplace.domain.User;
import com.halcyon.uniplace.engine.EngineClient;
import com.halcyon.uniplace.engine.EngineDtos.AllowanceLine;
import com.halcyon.uniplace.engine.EngineDtos.AllowanceRequest;
import com.halcyon.uniplace.engine.EngineDtos.AllowanceResult;
import com.halcyon.uniplace.engine.EngineUnavailableException;
import com.halcyon.uniplace.repository.NotificationRepository;
import com.halcyon.uniplace.repository.OfferRepository;
import com.halcyon.uniplace.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * White-box and state-based tests of OfferService against OFF-01 to OFF-04, NOT-04 and ADM-01.
 * Designed in docs/test-cases.md as TC-OFF-012 to TC-OFF-019. The Engine is stubbed, and the clock is under
 * the test's control so that the 72-hour rules can be tested without waiting.
 */
class OfferRulesTest extends IntegrationTest {

    static final ZoneId QATAR = ZoneId.of("Asia/Qatar");

    /** A clock the test can set and move forward. */
    static class MutableClock extends Clock {
        private volatile Instant now = Instant.now();

        void set(LocalDateTime qatarTime) { now = qatarTime.atZone(QATAR).toInstant(); }
        void advance(Duration d) { now = now.plus(d); }

        @Override public ZoneId getZone() { return QATAR; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @TestConfiguration
    static class ClockOverride {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }
    }

    @Autowired OfferService service;
    @Autowired OfferRepository offers;
    @Autowired NotificationRepository notifications;
    @Autowired SettingsService settings;
    @Autowired MutableClock testClock;
    @MockBean EngineClient engine;

    Company company;
    User recruiter;

    @BeforeEach
    void setUp() {
        // Today at 15:00 Qatar time: an issue time that is not midnight.
        testClock.set(LocalDate.now(QATAR).atTime(15, 0));
        company = company("Gulf Bank", CompanyStatus.APPROVED);
        recruiter = recruiter(company);
        when(engine.allowance(any())).thenReturn(new AllowanceResult(new BigDecimal("9000.00"),
                List.of(new AllowanceLine("Total", new BigDecimal("9000.00")))));
    }

    private OfferForm form(Posting p) {
        OfferForm f = new OfferForm();
        f.setMonthlyAllowance(p.getMonthlyAllowance());
        f.setStartDate(p.getStartDate());
        f.setEndDate(p.getEndDate());
        f.setWeeklyHours(40);
        return f;
    }

    private ApplicationState stateOf(Application a) {
        return applications.findById(a.getId()).orElseThrow().getState();
    }

    @Test
    @DisplayName("TC-OFF-012 / OFF-02: the response deadline is exactly 72 hours after the offer is issued")
    void responseDeadlineIsSeventyTwoHoursAfterIssue() {
        Posting p = openPosting(company, "Risk Intern", List.of("Python"), 2);
        Application a = application(student("3.00", List.of("Python")), p, ApplicationState.INTERVIEW, 70);
        LocalDateTime issuedAt = LocalDateTime.now(testClock);

        Offer o = service.issue(recruiter, a.getId(), form(p));

        assertEquals(issuedAt.plusHours(72), o.getRespondBy());
    }

    @Test
    @DisplayName("TC-OFF-013 / OFF-02: an offer cannot be accepted after its response deadline")
    void offerCannotBeAcceptedAfterDeadline() {
        Posting p = openPosting(company, "Risk Intern", List.of("Python"), 2);
        Student s = student("3.00", List.of("Python"));
        User su = studentUser(s);
        Application a = application(s, p, ApplicationState.INTERVIEW, 70);
        Offer o = service.issue(recruiter, a.getId(), form(p));

        testClock.advance(Duration.ofHours(73));   // the expiry job has not run yet

        assertThrows(BusinessRuleException.class, () -> service.accept(su, o.getId()));
    }

    @Test
    @DisplayName("TC-OFF-014 / OFF-03: accepting an offer withdraws the student's other active applications")
    void acceptingWithdrawsOtherActiveApplications() {
        Posting offered = openPosting(company, "Risk Intern", List.of("Python"), 2);
        Posting other1 = openPosting(company, "Data Intern", List.of("Python"), 2);
        Posting other2 = openPosting(company, "Audit Intern", List.of("Python"), 2);
        Student s = student("3.00", List.of("Python"));
        User su = studentUser(s);
        Application a = application(s, offered, ApplicationState.INTERVIEW, 70);
        Application b = application(s, other1, ApplicationState.SUBMITTED, 70);
        Application c = application(s, other2, ApplicationState.SHORTLISTED, 70);
        Offer o = service.issue(recruiter, a.getId(), form(offered));

        service.accept(su, o.getId());

        assertAll(
                () -> assertEquals(ApplicationState.ACCEPTED, stateOf(a)),
                () -> assertEquals(ApplicationState.WITHDRAWN, stateOf(b), "SUBMITTED application"),
                () -> assertEquals(ApplicationState.WITHDRAWN, stateOf(c), "SHORTLISTED application"));
    }

    @Test
    @DisplayName("TC-OFF-015 / OFF-03, NOT-01: taking the last seat fills the posting and rejects the remaining applications")
    void fillingLastSeatRejectsRemainingApplications() {
        Posting p = openPosting(company, "Risk Intern", List.of("Python"), 1);
        Student winner = student("3.00", List.of("Python"));
        User wu = studentUser(winner);
        Student other1 = student("3.10", List.of("Python"));
        User o1u = studentUser(other1);
        Student other2 = student("3.20", List.of("Python"));
        User o2u = studentUser(other2);
        Application a = application(winner, p, ApplicationState.INTERVIEW, 70);
        Application b = application(other1, p, ApplicationState.SUBMITTED, 70);
        Application c = application(other2, p, ApplicationState.SHORTLISTED, 70);
        Offer o = service.issue(recruiter, a.getId(), form(p));

        service.accept(wu, o.getId());

        assertAll(
                () -> assertEquals(PostingStatus.FILLED, postings.findById(p.getId()).orElseThrow().getStatus()),
                () -> assertEquals(ApplicationState.REJECTED, stateOf(b), "SUBMITTED application"),
                () -> assertEquals(ApplicationState.REJECTED, stateOf(c), "SHORTLISTED application"),
                () -> assertEquals(1, notifications.findByRecipient(o1u).size(), "notification to rejected applicant 1"),
                () -> assertEquals(1, notifications.findByRecipient(o2u).size(), "notification to rejected applicant 2"));
    }

    @Test
    @DisplayName("TC-OFF-016 / OFF-01, APP-06: an offer can only be issued to an application in INTERVIEW")
    void offerOnlyForApplicationInInterview() {
        Posting p = openPosting(company, "Risk Intern", List.of("Python"), 2);
        Application a = application(student("3.00", List.of("Python")), p, ApplicationState.SHORTLISTED, 70);

        assertThrows(BusinessRuleException.class, () -> service.issue(recruiter, a.getId(), form(p)));
        assertEquals(ApplicationState.SHORTLISTED, stateOf(a));
    }

    @Test
    @DisplayName("TC-OFF-017 / OFF-04: when two students accept the last seat at the same moment, exactly one succeeds")
    void concurrentAcceptsOfLastSeat() throws Exception {
        int rounds = 10;
        int roundsWithTwoAcceptances = 0;
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < rounds; round++) {
                Posting p = openPosting(company, "Last Seat " + round, List.of("Python"), 1);
                Student s1 = student("3.00", List.of("Python"));
                Student s2 = student("3.00", List.of("Python"));
                User u1 = studentUser(s1);
                User u2 = studentUser(s2);
                Offer o1 = service.issue(recruiter, application(s1, p, ApplicationState.INTERVIEW, 70).getId(), form(p));
                Offer o2 = service.issue(recruiter, application(s2, p, ApplicationState.INTERVIEW, 70).getId(), form(p));

                CountDownLatch start = new CountDownLatch(1);
                Future<Boolean> f1 = pool.submit(() -> acceptAfter(start, u1, o1));
                Future<Boolean> f2 = pool.submit(() -> acceptAfter(start, u2, o2));
                start.countDown();
                int succeeded = (f1.get(30, TimeUnit.SECONDS) ? 1 : 0) + (f2.get(30, TimeUnit.SECONDS) ? 1 : 0);
                if (succeeded != 1) {
                    roundsWithTwoAcceptances++;
                }
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(0, roundsWithTwoAcceptances,
                "rounds (of " + rounds + ") in which both students accepted the only seat");
    }

    private boolean acceptAfter(CountDownLatch start, User student, Offer offer) throws InterruptedException {
        start.await();
        try {
            service.accept(student, offer.getId());
            return true;
        } catch (RuntimeException refused) {
            return false;
        }
    }

    @Test
    @DisplayName("TC-OFF-018 / NOT-04: the offer reminder is recorded once, not every time the job runs")
    void reminderIsRecordedOnce() {
        testClock.set(LocalDate.now(QATAR).atStartOfDay());   // midnight, so the deadline is exactly +72 h
        Posting p = openPosting(company, "Risk Intern", List.of("Python"), 2);
        Student s = student("3.00", List.of("Python"));
        User su = studentUser(s);
        Application a = application(s, p, ApplicationState.INTERVIEW, 70);
        service.issue(recruiter, a.getId(), form(p));

        testClock.advance(Duration.ofHours(60));   // inside the last 24 hours of the window
        service.sendReminders();
        testClock.advance(Duration.ofMinutes(10));  // the scheduler runs every 10 minutes
        service.sendReminders();

        long reminders = notifications.findByRecipient(su).stream()
                .map(Notification::getSubject).filter(subject -> subject.startsWith("Reminder")).count();
        assertEquals(1, reminders);
    }

    @Test
    @DisplayName("TC-OFF-019 / ADM-01, MAINT-02: the monthly cap from Settings is sent to the Engine")
    void monthlyCapSettingIsSentToEngine() {
        settings.set(SettingsService.MONTHLY_CAP, "4000");
        Posting p = openPosting(company, "Risk Intern", List.of("Python"), 2);
        Student s = student("3.00", List.of("Python"));
        User su = studentUser(s);
        Offer o = service.issue(recruiter, application(s, p, ApplicationState.INTERVIEW, 70).getId(), form(p));

        service.accept(su, o.getId());

        ArgumentCaptor<AllowanceRequest> sent = ArgumentCaptor.forClass(AllowanceRequest.class);
        verify(engine, atLeastOnce()).allowance(sent.capture());
        BigDecimal cap = sent.getValue().monthlyCap;
        assertEquals(0, new BigDecimal("4000").compareTo(cap == null ? BigDecimal.ZERO : cap),
                "monthlyCap sent to the Engine: " + cap);
    }

    @Test
    @DisplayName("TC-OFF-031 / REL-01: accepting an offer while the Engine is down is refused and changes nothing")
    void acceptWhileEngineDownIsRefused() {
        Posting p = openPosting(company, "Risk Intern", List.of("Python"), 2);
        Student s = student("3.00", List.of("Python"));
        User su = studentUser(s);
        Application a = application(s, p, ApplicationState.INTERVIEW, 70);
        Offer o = service.issue(recruiter, a.getId(), form(p));
        when(engine.allowance(any())).thenThrow(new EngineUnavailableException());

        assertThrows(BusinessRuleException.class, () -> service.accept(su, o.getId()),
                "the acceptance went through without an allowance");
        assertAll(
                () -> assertEquals(OfferStatus.PENDING, offers.findById(o.getId()).orElseThrow().getStatus()),
                () -> assertEquals(ApplicationState.OFFERED, stateOf(a)),
                () -> assertEquals(0, postings.findById(p.getId()).orElseThrow().getSeatsTaken()));
    }
}
