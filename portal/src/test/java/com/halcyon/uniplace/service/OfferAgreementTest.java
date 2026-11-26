package com.halcyon.uniplace.service;

import com.halcyon.uniplace.domain.Application;
import com.halcyon.uniplace.domain.ApplicationState;
import com.halcyon.uniplace.domain.Company;
import com.halcyon.uniplace.domain.CompanyStatus;
import com.halcyon.uniplace.domain.Notification;
import com.halcyon.uniplace.domain.Offer;
import com.halcyon.uniplace.domain.OfferStatus;
import com.halcyon.uniplace.domain.Posting;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

/**
 * OFF-07 (agreement date and recalculation), REL-01 for the recalculation, and the NOT-01 / NOT-02 notifications
 * of the offer life cycle. Designed in docs/test-cases.md as TC-OFF-021 to TC-OFF-028. The Engine is stubbed.
 */
@AutoConfigureMockMvc
class OfferAgreementTest extends IntegrationTest {

    @Autowired OfferService service;
    @Autowired OfferRepository offers;
    @Autowired NotificationRepository notifications;
    @Autowired MockMvc mvc;
    @MockBean EngineClient engine;

    Company company;
    User recruiter;
    Posting posting;
    Student student;
    User studentUser;

    @BeforeEach
    void setUp() {
        company = company("Gulf Bank", CompanyStatus.APPROVED);
        recruiter = recruiter(company);
        posting = openPosting(company, "Risk Intern", List.of("Python"), 2);
        student = student("3.00", List.of("Python"));
        studentUser = studentUser(student);
        when(engine.allowance(any())).thenReturn(new AllowanceResult(new BigDecimal("8550.00"), List.of(
                new AllowanceLine("Base allowance", new BigDecimal("9000.00")),
                new AllowanceLine("Late documents penalty", new BigDecimal("-450.00")),
                new AllowanceLine("Total", new BigDecimal("8550.00")))));
    }

    /** An accepted placement whose internship started 10 days ago, so that an agreement can be late. */
    private Offer acceptedPlacementStartedTenDaysAgo() {
        LocalDate start = today().minusDays(10);
        posting.setStartDate(start);
        posting.setEndDate(start.plusMonths(3));
        postings.save(posting);
        Application a = application(student, posting, ApplicationState.ACCEPTED, 70);
        LocalDateTime issued = LocalDateTime.now(clock).minusDays(20);
        Offer o = new Offer(a, new BigDecimal("3000"), start, start.plusMonths(3), 40, issued, issued.plusHours(72));
        o.setStatus(OfferStatus.ACCEPTED);
        return offers.save(o);
    }

    private Offer pendingOffer(LocalDateTime issuedAt, LocalDateTime respondBy) {
        Application a = application(student, posting, ApplicationState.OFFERED, 70);
        return offers.save(new Offer(a, new BigDecimal("3000"), posting.getStartDate(), posting.getEndDate(), 40,
                issuedAt, respondBy));
    }

    private AllowanceRequest lastAllowanceRequest() {
        ArgumentCaptor<AllowanceRequest> sent = ArgumentCaptor.forClass(AllowanceRequest.class);
        verify(engine).allowance(sent.capture());
        return sent.getValue();
    }

    /** NOT-02: the body names the posting and the company. */
    private void assertNamesPostingAndCompany(Notification n) {
        assertAll(
                () -> assertTrue(n.getBody().contains("Risk Intern"), "posting in: " + n.getBody()),
                () -> assertTrue(n.getBody().contains("Gulf Bank"), "company in: " + n.getBody()));
    }

    @ParameterizedTest(name = "agreement {0} day(s) after the start: {0} late days")
    @CsvSource({"0", "1", "8"})
    @DisplayName("TC-OFF-021 / OFF-07: the agreement date sets the late days sent to the Engine, and the statement is stored")
    void agreementDateSetsLateDays(int daysAfterStart) {
        Offer o = acceptedPlacementStartedTenDaysAgo();
        LocalDate receivedOn = o.getStartDate().plusDays(daysAfterStart);

        service.recordAgreement(o.getApplication().getId(), receivedOn);

        Offer saved = offers.findById(o.getId()).orElseThrow();
        assertAll(
                () -> assertEquals(daysAfterStart, lastAllowanceRequest().lateDays, "lateDays sent to the Engine"),
                () -> assertEquals(receivedOn, applications.findById(o.getApplication().getId()).orElseThrow()
                        .getAgreementReceivedOn()),
                () -> assertEquals(0, new BigDecimal("8550.00").compareTo(saved.getAllowanceTotal()), "stored total"),
                () -> assertEquals(3, service.linesOf(saved).size(), "stored statement lines"));
    }

    @Test
    @DisplayName("TC-OFF-022 / OFF-07: an agreement date in the future is refused")
    void agreementDateInTheFutureIsRefused() {
        Offer o = acceptedPlacementStartedTenDaysAgo();

        assertThrows(BusinessRuleException.class,
                () -> service.recordAgreement(o.getApplication().getId(), today().plusDays(1)));
        assertNull(applications.findById(o.getApplication().getId()).orElseThrow().getAgreementReceivedOn());
    }

    @Test
    @DisplayName("TC-OFF-022 / OFF-07: an agreement date is refused for a placement that is not accepted")
    void agreementOnlyForAcceptedPlacement() {
        Offer o = pendingOffer(LocalDateTime.now(clock), LocalDateTime.now(clock).plusHours(72));

        assertThrows(BusinessRuleException.class, () -> service.recordAgreement(o.getApplication().getId(), today()));
    }

    @Test
    @DisplayName("TC-OFF-023 / OFF-07: until an agreement date is recorded no penalty applies")
    void noPenaltyBeforeAnAgreementDate() {
        Offer o = acceptedPlacementStartedTenDaysAgo();

        service.recalculate(o.getId());

        assertEquals(0, lastAllowanceRequest().lateDays);
    }

    @Test
    @DisplayName("TC-OFF-024 / REL-01, OFF-07: when the Engine is down, recording the agreement is refused with a message and nothing is stored")
    void engineDownWhileRecordingAgreement() throws Exception {
        Offer o = acceptedPlacementStartedTenDaysAgo();
        Long applicationId = o.getApplication().getId();
        when(engine.allowance(any())).thenThrow(new EngineUnavailableException());

        mvc.perform(post("/coordinator/applications/" + applicationId + "/agreement")
                        .param("receivedOn", today().minusDays(2).toString())
                        .with(user("coordinator").roles("COORDINATOR")).with(csrf()))
                .andExpect(redirectedUrl("/applications/" + applicationId))
                .andExpect(flash().attributeExists("error"));

        assertNull(applications.findById(applicationId).orElseThrow().getAgreementReceivedOn(),
                "the agreement date must not be stored when the allowance could not be recalculated");
    }

    @Test
    @DisplayName("TC-OFF-025 / NOT-01, NOT-02: issuing an offer notifies the student, naming the posting and company")
    void issuingNotifiesTheStudent() {
        Application a = application(student, posting, ApplicationState.INTERVIEW, 70);
        OfferForm f = new OfferForm();
        f.setMonthlyAllowance(posting.getMonthlyAllowance());
        f.setStartDate(posting.getStartDate());
        f.setEndDate(posting.getEndDate());
        f.setWeeklyHours(40);

        service.issue(recruiter, a.getId(), f);

        List<Notification> sent = notifications.findByRecipient(studentUser);
        assertEquals(1, sent.size());
        assertNamesPostingAndCompany(sent.get(0));
    }

    @Test
    @DisplayName("TC-OFF-026 / NOT-01, NOT-02: accepting an offer notifies the student and the recruiters")
    void acceptingNotifiesStudentAndRecruiters() {
        Offer o = pendingOffer(LocalDateTime.now(clock), LocalDateTime.now(clock).plusHours(72));

        service.accept(studentUser, o.getId());

        List<Notification> toStudent = notifications.findByRecipient(studentUser);
        List<Notification> toRecruiter = notifications.findByRecipient(recruiter);
        assertAll(
                () -> assertEquals(1, toStudent.size(), "notifications to the student"),
                () -> assertEquals(1, toRecruiter.size(), "notifications to the recruiter"),
                () -> assertNamesPostingAndCompany(toStudent.get(0)),
                () -> assertNamesPostingAndCompany(toRecruiter.get(0)));
    }

    @Test
    @DisplayName("TC-OFF-027 / NOT-01, APP-06: declining rejects the application, declines the offer and notifies")
    void decliningRejectsAndNotifies() {
        Offer o = pendingOffer(LocalDateTime.now(clock), LocalDateTime.now(clock).plusHours(72));

        service.decline(studentUser, o.getId());

        List<Notification> toRecruiter = notifications.findByRecipient(recruiter);
        assertAll(
                () -> assertEquals(OfferStatus.DECLINED, offers.findById(o.getId()).orElseThrow().getStatus()),
                () -> assertEquals(ApplicationState.REJECTED,
                        applications.findById(o.getApplication().getId()).orElseThrow().getState()),
                () -> assertEquals(1, toRecruiter.size(), "notifications to the recruiter"),
                () -> assertNamesPostingAndCompany(toRecruiter.get(0)));
    }

    @Test
    @DisplayName("TC-OFF-028 / OFF-02, NOT-01: an unanswered offer expires, the application is withdrawn and both sides are notified")
    void unansweredOfferExpires() {
        LocalDateTime now = LocalDateTime.now(clock);
        Offer o = pendingOffer(now.minusHours(80), now.minusHours(8));

        int expired = service.expireOverdue();

        List<Notification> toStudent = notifications.findByRecipient(studentUser);
        List<Notification> toRecruiter = notifications.findByRecipient(recruiter);
        assertAll(
                () -> assertEquals(1, expired),
                () -> assertEquals(OfferStatus.EXPIRED, offers.findById(o.getId()).orElseThrow().getStatus()),
                () -> assertEquals(ApplicationState.WITHDRAWN,
                        applications.findById(o.getApplication().getId()).orElseThrow().getState()),
                () -> assertEquals(1, toStudent.size(), "notifications to the student"),
                () -> assertEquals(1, toRecruiter.size(), "notifications to the recruiter"),
                () -> assertNamesPostingAndCompany(toStudent.get(0)));
    }
}
