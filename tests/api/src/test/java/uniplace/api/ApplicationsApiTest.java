package uniplace.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Black-box tests of POST /eligibility, /match and /rank (APP-04, APP-09, STU-04, REL-02), designed in
 * docs/test-cases.md as TC-APP-018 to TC-APP-025. The oracle is the Requirements and the OpenAPI description of
 * each endpoint, worked out by hand. Issues: #2, #32 to #37.
 */
class ApplicationsApiTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String AS_OF = "2026-11-24";

    // ------------------------------------------------------------------ helpers

    /** An eligible GOOD student: GPA 3.00 against 2.50, 60 credits against 30, valid CV and transcript. */
    private static ObjectNode eligibility() {
        ObjectNode r = JSON.createObjectNode();
        r.put("gpa", 3.00);
        r.put("minGpa", 2.50);
        r.put("creditsCompleted", 60);
        r.put("minCredits", 30);
        r.put("standing", "GOOD");
        r.put("priorInternships", 0);
        r.put("asOf", AS_OF);
        ArrayNode docs = r.putArray("documents");
        docs.add(document("CV", 100_000, "application/pdf", null));
        docs.add(document("TRANSCRIPT", 100_000, "application/pdf", "2026-11-01"));
        return r;
    }

    private static ObjectNode eligibility(Consumer<ObjectNode> change) {
        ObjectNode r = eligibility();
        change.accept(r);
        return r;
    }

    private static ObjectNode document(String type, long size, String mime, String issueDate) {
        ObjectNode d = JSON.createObjectNode();
        d.put("type", type);
        d.put("sizeBytes", size);
        d.put("mimeType", mime);
        if (issueDate != null) {
            d.put("issueDate", issueDate);
        }
        return d;
    }

    private static JsonNode ok(String path, ObjectNode body) {
        EngineServer.Response r = EngineServer.post(path, body.toString());
        assertEquals(200, r.status(), () -> "HTTP status for " + body + ": " + r.raw());
        return r.json();
    }

    private static boolean eligible(ObjectNode body) {
        return ok("/eligibility", body).get("eligible").asBoolean();
    }

    private static ObjectNode match(List<String> student, List<String> required, List<String> nice) {
        ObjectNode r = JSON.createObjectNode();
        student.forEach(r.putArray("studentSkills")::add);
        required.forEach(r.putArray("requiredSkills")::add);
        nice.forEach(r.putArray("niceToHaveSkills")::add);
        return r;
    }

    private static int score(ObjectNode body) {
        return ok("/match", body).get("score").asInt();
    }

    private static ObjectNode candidate(long id, int score, double gpa, String submittedAt) {
        ObjectNode c = JSON.createObjectNode();
        c.put("applicationId", id);
        c.put("score", score);
        c.put("gpa", gpa);
        c.put("submittedAt", submittedAt);
        return c;
    }

    private static ObjectNode rank(int seats, ObjectNode... candidates) {
        ObjectNode r = JSON.createObjectNode();
        r.put("seats", seats);
        ArrayNode list = r.putArray("candidates");
        for (ObjectNode c : candidates) {
            list.add(c);
        }
        return r;
    }

    private static List<Long> ids(JsonNode array) {
        List<Long> out = new ArrayList<>();
        array.forEach(n -> out.add(n.asLong()));
        return out;
    }

    /** Five candidates with scores 10 to 50, so the ranking is 5, 4, 3, 2, 1. */
    private static ObjectNode fiveCandidates(int seats) {
        String t = "2026-11-20T10:00:00+03:00";
        return rank(seats, candidate(1, 10, 3.0, t), candidate(2, 20, 3.0, t), candidate(3, 30, 3.0, t),
                candidate(4, 40, 3.0, t), candidate(5, 50, 3.0, t));
    }

    // ------------------------------------------------------------------ TC-APP-018 / APP-04: GPA bound (#2)

    @ParameterizedTest(name = "gpa {0} against minGpa 2.50: eligible = {1}")
    @CsvSource({"2.49, false", "2.50, true", "2.51, true"})
    @DisplayName("TC-APP-018 / APP-04, POS-01: the GPA must be at least the minimum (#2)")
    void gpaBoundary(double gpa, boolean expected) {
        assertEquals(expected, eligible(eligibility(r -> r.put("gpa", gpa))));
    }

    // ------------------------------------------------------------------ TC-APP-019 / STU-04: transcript (#33)

    @ParameterizedTest(name = "transcript issued {0} (day {1} on 2026-11-24): eligible = {2}")
    @CsvSource({"2026-11-24, 1, true", "2026-05-30, 179, true", "2026-05-29, 180, true", "2026-05-28, 181, false"})
    @DisplayName("TC-APP-019 / STU-04: a transcript is valid for 180 days, the issue date being day 1 (#33)")
    void transcriptValidity(String issueDate, int day, boolean expected) {
        ObjectNode body = eligibility(r -> {
            ArrayNode docs = r.putArray("documents");
            docs.add(document("CV", 100_000, "application/pdf", null));
            docs.add(document("TRANSCRIPT", 100_000, "application/pdf", issueDate));
        });
        assertEquals(expected, eligible(body), "day " + day);
    }

    // ------------------------------------------------------------------ TC-APP-020 / APP-04: decision table

    @Test
    @DisplayName("TC-APP-020 / APP-04: every failed rule is reported (GPA, credits, CV, transcript)")
    void everyFailedRuleIsReported() {
        JsonNode r = ok("/eligibility", eligibility(b -> {
            b.put("gpa", 1.0);
            b.put("creditsCompleted", 10);
            b.putArray("documents");
        }));
        assertAll(
                () -> assertEquals(false, r.get("eligible").asBoolean()),
                () -> assertEquals(4, r.get("reasons").size(), r.toString()));
    }

    @ParameterizedTest(name = "PROBATION with {0} prior internship(s): eligible = {1}")
    @CsvSource({"1, true", "2, false"})
    @DisplayName("TC-APP-020 / APP-04: a PROBATION student needs fewer than 2 prior internships")
    void probationAndPriorInternships(int prior, boolean expected) {
        assertEquals(expected, eligible(eligibility(r -> {
            r.put("standing", "PROBATION");
            r.put("minGpa", 1.50);
            r.put("priorInternships", prior);
        })));
    }

    @ParameterizedTest(name = "CV of {0} bytes, {1}: eligible = {2}")
    @CsvSource({"2097152, application/pdf, true", "2097153, application/pdf, false", "100, application/msword, false"})
    @DisplayName("TC-APP-020 / STU-04: the CV must be a PDF of at most 2 MB")
    void cvSizeAndType(long size, String mime, boolean expected) {
        assertEquals(expected, eligible(eligibility(r -> {
            ArrayNode docs = r.putArray("documents");
            docs.add(document("CV", size, mime, null));
            docs.add(document("TRANSCRIPT", 100, "application/pdf", "2026-11-01"));
        })));
    }

    // ------------------------------------------------------------------ TC-APP-021 / REL-02 (#37)

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"gpa=4.01", "gpa=-0.1", "standing=FOO", "creditsCompleted=141"})
    @DisplayName("TC-APP-021 / REL-02: an out-of-domain value is answered with 400 naming the field (#37)")
    void outOfDomainValueIs400(String change) {
        String field = change.substring(0, change.indexOf('='));
        String value = change.substring(change.indexOf('=') + 1);
        ObjectNode body = eligibility(r -> {
            if (field.equals("standing")) {
                r.put(field, value);
            } else if (field.equals("creditsCompleted")) {
                r.put(field, Integer.parseInt(value));
            } else {
                r.put(field, Double.parseDouble(value));
                if (field.equals("gpa")) {
                    r.put("minGpa", 0);
                }
            }
        });
        EngineServer.Response r = EngineServer.post("/eligibility", body.toString());
        assertAll(
                () -> assertEquals(400, r.status(), r.raw()),
                () -> assertEquals(field, r.json().path("field").asText(), r.raw()));
    }

    // ------------------------------------------------------------------ TC-APP-022 / APP-04: skill comparison (#32)

    @ParameterizedTest(name = "student skill [{0}] against required [java]")
    @ValueSource(strings = {"java", "Java", "JAVA", " java", "java "})
    @DisplayName("TC-APP-022 / APP-04: skills are compared ignoring letter case and surrounding spaces (#32)")
    void skillComparisonIgnoresCaseAndSpaces(String skill) {
        assertEquals(70, score(match(List.of(skill), List.of("java"), List.of("git"))));
    }

    // ------------------------------------------------------------------ TC-APP-023 / APP-04: formula (#34)

    @ParameterizedTest(name = "{3}: expected {4}")
    @CsvSource(delimiter = ';', value = {
            "a,x; a,b,c; x,y; 1/3 required + 1/2 nice; 38",
            "a,x; a,b; x,y; 1/2 required + 1/2 nice; 50",
            "java,git; java; git; all required + all nice; 100",
            "a,b; a,b,c; ; 2/3 required, empty nice list; 47",
            "x; java; ; no required skill, empty nice list; 0"})
    @DisplayName("TC-APP-023 / APP-04: score = 70 x required share + 30 x nice share (0 when the nice list is empty) (#34)")
    void scoreFormula(String student, String required, String nice, String label, int expected) {
        assertEquals(expected, score(match(split(student), split(required), split(nice))), label);
    }

    private static List<String> split(String csv) {
        return csv == null || csv.isBlank() ? List.of() : List.of(csv.split(","));
    }

    // ------------------------------------------------------------------ TC-APP-024 / APP-09: tie-breaks (#35)

    @Test
    @DisplayName("TC-APP-024 / APP-09: on equal scores the higher GPA is ranked first (#35)")
    void higherGpaBreaksATie() {
        JsonNode r = ok("/rank", rank(1,
                candidate(1, 80, 3.0, "2026-11-20T10:00:00+03:00"),
                candidate(2, 80, 3.5, "2026-11-21T10:00:00+03:00")));
        assertEquals(List.of(2L, 1L), ids(r.get("ranked")));
    }

    @Test
    @DisplayName("TC-APP-024 / APP-09: on equal score and GPA the earlier submission is ranked first, across time zones")
    void earlierSubmissionBreaksATieAcrossTimeZones() {
        JsonNode r = ok("/rank", rank(1,
                candidate(10, 50, 3.0, "2026-11-20T07:00:00Z"),
                candidate(11, 50, 3.0, "2026-11-20T09:00:00+03:00")));
        assertEquals(List.of(11L, 10L), ids(r.get("ranked")), "09:00+03:00 is 06:00Z, one hour before 07:00Z");
    }

    // ------------------------------------------------------------------ TC-APP-025 / APP-09: aboveCut (#36)

    @ParameterizedTest(name = "{0} seat(s) for 5 candidates")
    @ValueSource(ints = {1, 2, 3, 5})
    @DisplayName("TC-APP-025 / APP-09: aboveCut holds exactly the first 'seats' candidates (#36)")
    void aboveCutHasAsManyCandidatesAsSeats(int seats) {
        JsonNode r = ok("/rank", fiveCandidates(seats));
        List<Long> ranked = ids(r.get("ranked"));
        assertAll(
                () -> assertEquals(List.of(5L, 4L, 3L, 2L, 1L), ranked),
                () -> assertEquals(ranked.subList(0, seats), ids(r.get("aboveCut"))));
    }

    @Test
    @DisplayName("TC-APP-025 / REL-02: seats of 0 is answered with 400 naming the field")
    void zeroSeatsIs400() {
        EngineServer.Response r = EngineServer.post("/rank", fiveCandidates(0).toString());
        assertAll(
                () -> assertEquals(400, r.status(), r.raw()),
                () -> assertTrue(r.raw().contains("seats"), r.raw()));
    }
}
