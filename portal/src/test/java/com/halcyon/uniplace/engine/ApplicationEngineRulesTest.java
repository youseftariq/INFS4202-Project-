package com.halcyon.uniplace.engine;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.halcyon.uniplace.domain.Company;
import com.halcyon.uniplace.domain.Posting;
import com.halcyon.uniplace.engine.EngineDtos.EligibilityRequest;
import com.halcyon.uniplace.engine.EngineDtos.EligibilityResult;
import com.halcyon.uniplace.engine.EngineDtos.MatchRequest;
import com.halcyon.uniplace.engine.EngineDtos.MatchResult;
import com.halcyon.uniplace.service.SettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

/**
 * White-box tests of how the Portal obtains eligibility and match scores from the Engine (APP-04, REL-01).
 * Designed in docs/test-cases.md as TC-APP-009 (data flow of the match score through MatchScoreService's cache,
 * #40) and TC-APP-011 (Engine unreachable, #39).
 */
@WireMockTest
class ApplicationEngineRulesTest {

    EngineClient client;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wm) {
        SettingsService settings = Mockito.mock(SettingsService.class);
        when(settings.engineBaseUrl()).thenReturn("http://127.0.0.1:" + wm.getHttpPort() + "/api/v1");
        client = new EngineClient(RestClient.builder(), settings);
    }

    private static EligibilityRequest eligibility() {
        EligibilityRequest r = new EligibilityRequest();
        r.gpa = new BigDecimal("1.85");
        r.creditsCompleted = 60;
        r.standing = "PROBATION";
        r.minGpa = new BigDecimal("2.50");
        r.minCredits = 60;
        r.asOf = LocalDate.of(2026, 11, 24);
        return r;
    }

    private static MatchRequest match() {
        MatchRequest m = new MatchRequest();
        m.studentSkills = List.of("Excel", "SQL");
        m.requiredSkills = List.of("Python", "SQL", "Statistics");
        m.niceToHaveSkills = List.of("Excel");
        return m;
    }

    /** Risk Analytics Intern from the seed data: required Python, SQL, Statistics; nice-to-have Excel. */
    private static Posting riskAnalytics() {
        Posting p = new Posting(new Company("Gulf Bank", "Banking", "hr@gulfbank.qa"), "Risk Analytics Intern", "D");
        ReflectionTestUtils.setField(p, "id", 4L);
        p.setRequiredSkills(List.of("Python", "SQL", "Statistics"));
        p.setNiceToHaveSkills(List.of("Excel"));
        return p;
    }

    // ------------------------------------------------------------------ TC-APP-009 / APP-04 (#40)

    @Test
    @DisplayName("TC-APP-009 / APP-04: two students with different skills get their own match scores for one posting (#40)")
    void matchScoreDependsOnTheStudentsSkills() {
        EngineClient engine = Mockito.mock(EngineClient.class);
        // The Engine's answer depends on the student's skills, as the /match formula does.
        when(engine.match(any())).thenAnswer(call -> {
            MatchRequest m = call.getArgument(0);
            return new MatchResult(m.studentSkills.contains("Python") ? 47 : 53, List.of(), List.of(), List.of());
        });
        MatchScoreService scores = new MatchScoreService(engine,
                new EngineRequestMapper(Mockito.mock(SettingsService.class), Clock.systemUTC()));
        Posting p = riskAnalytics();

        int amal = scores.score(List.of("Java", "SQL", "Git", "Spring", "Python"), p);
        int omar = scores.score(List.of("Excel", "SQL", "Communication"), p);

        assertAll(
                () -> assertEquals(47, amal, "first student"),
                () -> assertEquals(53, omar, "second student, different skills: the Engine must be asked again"),
                () -> Mockito.verify(engine, times(2)).match(any()));
    }

    @Test
    @DisplayName("TC-APP-009 / APP-04: the same skills on the same posting reuse the cached score")
    void sameSkillsReuseTheCachedScore() {
        EngineClient engine = Mockito.mock(EngineClient.class);
        when(engine.match(any())).thenReturn(new MatchResult(47, List.of(), List.of(), List.of()));
        MatchScoreService scores = new MatchScoreService(engine,
                new EngineRequestMapper(Mockito.mock(SettingsService.class), Clock.systemUTC()));
        Posting p = riskAnalytics();

        scores.score(List.of("Python", "SQL"), p);
        scores.score(List.of("Python", "SQL"), p);

        Mockito.verify(engine, times(1)).match(any());
    }

    // ------------------------------------------------------------------ TC-APP-011 / REL-01 (#39)

    @Test
    @DisplayName("TC-APP-011 / REL-01: when the Engine connection fails, the student is never treated as eligible (#39)")
    void unreachableEngineNeverMeansEligible() {
        stubFor(post(urlEqualTo("/api/v1/eligibility"))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        EligibilityResult r;
        try {
            r = client.checkEligibility(eligibility());
        } catch (EngineUnavailableException expected) {
            return; // refusing with an error is the correct behaviour
        }
        assertFalse(r.eligible(), "Engine unreachable, but the Portal answered 'eligible' (REL-01)");
    }

    @Test
    @DisplayName("TC-APP-011 / REL-01: when the Engine answers 500, the student is never treated as eligible (#39)")
    void engineErrorNeverMeansEligible() {
        stubFor(post(urlEqualTo("/api/v1/eligibility")).willReturn(aResponse().withStatus(500)));

        EligibilityResult r;
        try {
            r = client.checkEligibility(eligibility());
        } catch (EngineUnavailableException expected) {
            return;
        }
        assertFalse(r.eligible(), "Engine error, but the Portal answered 'eligible' (REL-01)");
    }

    @Test
    @DisplayName("TC-APP-011 / REL-01: when the Engine connection fails, no match score of 0 is invented (#39)")
    void unreachableEngineGivesNoMatchScore() {
        stubFor(post(urlEqualTo("/api/v1/match"))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThrows(EngineUnavailableException.class, () -> client.match(match()),
                "the Portal must refuse the operation instead of storing a score it did not get (REL-01)");
    }
}
