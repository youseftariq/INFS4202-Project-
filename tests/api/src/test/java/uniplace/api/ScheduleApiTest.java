package uniplace.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Black-box tests of POST /schedule/validate (interview slots; module Interviews, INT-01, INT-02, INT-03, INT-05), designed in
 * docs/test-cases.md as TC-INT-001 to TC-INT-010. The oracle is the OpenAPI description of /schedule/validate:
 * a slot is valid when it lasts 15 to 120 minutes, falls on Sunday to Thursday, starts at or after 08:00, ends at or
 * before 18:00 on the same day, and does not overlap another slot; a slot that starts exactly when another ends does
 * not overlap it.
 *
 * All times carry the offset +03:00 (Qatar) so that the tests do not depend on how the Engine reads a time
 * without an offset. 29/11/2026 is a Sunday.
 * Tests whose name ends in "Defect" fail while the matching defect is open: the failure is the evidence.
 */
class ScheduleApiTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SUN = "2026-11-29";

    private static ObjectNode slot(String start, String end) {
        ObjectNode s = JSON.createObjectNode();
        s.put("start", start);
        s.put("end", end);
        return s;
    }

    /** A slot on Sunday 29/11/2026 from "HH:mm" to "HH:mm", in Qatar time. */
    private static ObjectNode sunday(String from, String to) {
        return slot(SUN + "T" + from + ":00+03:00", SUN + "T" + to + ":00+03:00");
    }

    private static ObjectNode body(ObjectNode candidate, List<ObjectNode> recruiter, List<ObjectNode> student) {
        ObjectNode r = JSON.createObjectNode();
        r.set("candidate", candidate);
        ArrayNode rs = r.putArray("recruiterSlots");
        recruiter.forEach(rs::add);
        ArrayNode ss = r.putArray("studentSlots");
        student.forEach(ss::add);
        return r;
    }

    private static JsonNode validate(ObjectNode body) {
        EngineServer.Response r = EngineServer.post("/schedule/validate", body.toString());
        assertEquals(200, r.status(), () -> "HTTP status for " + body + ": " + r.raw());
        return r.json();
    }

    private static JsonNode validateCandidate(ObjectNode candidate) {
        return validate(body(candidate, List.of(), List.of()));
    }

    private static Executable expect(boolean valid, ObjectNode candidate, String what) {
        return () -> {
            JsonNode result = validateCandidate(candidate);
            assertEquals(valid, result.get("valid").asBoolean(), () -> what + ": expected valid=" + valid + " but got " + result);
        };
    }

    private static Executable expect(boolean valid, ObjectNode candidate, List<ObjectNode> recruiter, List<ObjectNode> student, String what) {
        return () -> {
            JsonNode result = validate(body(candidate, recruiter, student));
            assertEquals(valid, result.get("valid").asBoolean(), () -> what + ": expected valid=" + valid + " but got " + result);
        };
    }

    // ------------------------------------------------------------------ TC-INT-001 duration

    @Test
    @DisplayName("TC-INT-001 / INT-01: durations of 14, 15, 119 and 121 minutes (boundaries that work)")
    void durationBoundariesAroundTheLimits() {
        assertAll(
                expect(false, sunday("10:00", "10:14"), "14 minutes"),
                expect(true, sunday("10:00", "10:15"), "15 minutes"),
                expect(true, sunday("10:00", "11:59"), "119 minutes"),
                expect(false, sunday("10:00", "12:01"), "121 minutes"));
    }

    @Test
    @DisplayName("TC-INT-001 / INT-01: a slot of exactly 120 minutes is valid (DEFECT)")
    void slotOfExactlyTwoHoursIsValidDefect() {
        assertAll(expect(true, sunday("10:00", "12:00"), "120 minutes"));
    }

    // ------------------------------------------------------------------ TC-INT-002 days

    @Test
    @DisplayName("TC-INT-002 / INT-02: Sunday to Thursday are valid, Friday and Saturday are refused")
    void onlySundayToThursdayAreValid() {
        String[][] days = {{"2026-11-29", "true"}, {"2026-11-30", "true"}, {"2026-12-01", "true"}, {"2026-12-02", "true"},
                {"2026-12-03", "true"}, {"2026-12-04", "false"}, {"2026-12-05", "false"}};
        List<Executable> checks = new ArrayList<>();
        for (String[] d : days) {
            checks.add(expect(Boolean.parseBoolean(d[1]), slot(d[0] + "T10:00:00+03:00", d[0] + "T11:00:00+03:00"), d[0]));
        }
        assertAll(checks);
    }

    // ------------------------------------------------------------------ TC-INT-003 start of the day

    @Test
    @DisplayName("TC-INT-003 / INT-02: a slot starting at 07:59 is refused, one starting at 08:00 is valid")
    void startTimeBoundary() {
        assertAll(
                expect(false, sunday("07:59", "08:30"), "starts 07:59"),
                expect(true, sunday("08:00", "09:00"), "starts 08:00"),
                expect(false, sunday("07:00", "08:00"), "starts 07:00"));
    }

    // ------------------------------------------------------------------ TC-INT-004 end of the day

    @Test
    @DisplayName("TC-INT-004 / INT-02: a slot ending at 18:00 is valid, slots starting at or after 18:00 are refused")
    void endTimeBoundaryThatWorks() {
        assertAll(
                expect(true, sunday("17:00", "18:00"), "ends 18:00"),
                expect(false, sunday("18:00", "18:30"), "starts 18:00"),
                expect(false, sunday("19:00", "19:30"), "starts 19:00"));
    }

    @Test
    @DisplayName("TC-INT-004 / INT-02: a slot that ends after 18:00 is refused (DEFECT)")
    void slotEndingAfterSixIsRefusedDefect() {
        assertAll(
                expect(false, sunday("17:00", "18:01"), "17:00 to 18:01"),
                expect(false, sunday("17:30", "18:30"), "17:30 to 18:30"),
                expect(false, sunday("17:59", "18:30"), "17:59 to 18:30"));
    }

    // ------------------------------------------------------------------ TC-INT-005 real overlaps

    @Test
    @DisplayName("TC-INT-005 / INT-03: a real overlap with the recruiter's or the student's slots is refused")
    void realOverlapsAreRefused() {
        ObjectNode c = sunday("10:00", "11:00");
        assertAll(
                expect(false, c, List.of(sunday("10:59", "12:00")), List.of(), "recruiter overlaps the last minute"),
                expect(false, c, List.of(sunday("09:00", "10:01")), List.of(), "recruiter overlaps the first minute"),
                expect(false, c, List.of(sunday("10:15", "10:30")), List.of(), "recruiter slot inside the candidate"),
                expect(false, c, List.of(sunday("09:00", "12:00")), List.of(), "recruiter slot around the candidate"),
                expect(false, c, List.of(sunday("10:00", "11:00")), List.of(), "identical recruiter slot"),
                expect(false, c, List.of(), List.of(sunday("10:59", "11:30")), "student overlaps the last minute"),
                expect(false, c, List.of(), List.of(sunday("09:00", "10:01")), "student overlaps the first minute"),
                expect(false, c, List.of(), List.of(sunday("10:00", "11:00")), "identical student slot"));
    }

    @Test
    @DisplayName("TC-INT-005 / INT-03: a slot on another day or hours away does not conflict")
    void slotsThatDoNotOverlapAreIgnored() {
        ObjectNode c = sunday("10:00", "11:00");
        ObjectNode otherDay = slot("2026-11-30T10:00:00+03:00", "2026-11-30T11:00:00+03:00");
        assertAll(
                expect(true, c, List.of(otherDay), List.of(otherDay), "same hours on Monday"),
                expect(true, c, List.of(sunday("13:00", "14:00")), List.of(sunday("08:00", "09:00")), "hours away"));
    }

    // ------------------------------------------------------------------ TC-INT-006 touching slots

    @Test
    @DisplayName("TC-INT-006 / INT-03: back-to-back slots (one starts exactly when the other ends) do not overlap (DEFECT)")
    void backToBackSlotsAreValidDefect() {
        ObjectNode c = sunday("10:00", "11:00");
        assertAll(
                expect(true, c, List.of(sunday("11:00", "12:00")), List.of(), "recruiter slot starts when the candidate ends"),
                expect(true, c, List.of(sunday("09:00", "10:00")), List.of(), "recruiter slot ends when the candidate starts"),
                expect(true, c, List.of(), List.of(sunday("11:00", "12:00")), "student slot starts when the candidate ends"),
                expect(true, c, List.of(), List.of(sunday("09:00", "10:00")), "student slot ends when the candidate starts"));
    }

    // ------------------------------------------------------------------ TC-INT-007 several slots

    @Test
    @DisplayName("TC-INT-007 / INT-03: with several slots, every conflict is reported and the unrelated ones are not")
    void severalSlotsAreAllChecked() {
        ObjectNode c = sunday("10:00", "11:00");
        JsonNode result = validate(body(c,
                List.of(sunday("08:00", "09:00"), sunday("10:30", "11:30")),
                List.of(sunday("10:00", "10:30"))));
        assertFalse(result.get("valid").asBoolean(), () -> "expected a conflict: " + result);
        assertEquals(2, result.get("conflicts").size(), () -> "one conflict for the recruiter and one for the student: " + result);
        String all = result.get("conflicts").toString();
        assertTrue(all.contains("recruiter") && all.contains("student"), () -> "conflicts name who is busy: " + all);
    }

    // ------------------------------------------------------------------ TC-INT-008 invalid input

    @Test
    @DisplayName("TC-INT-008 / REL-02: invalid requests are answered with HTTP 400 and the field name, never 500")
    void invalidRequestsAre400() {
        String ok = "{\"start\":\"2026-11-29T10:00:00+03:00\",\"end\":\"2026-11-29T11:00:00+03:00\"}";
        String[][] cases = {
                {"{}", "candidate"},
                {"{\"candidate\":null}", "candidate"},
                {"{\"candidate\":\"abc\"}", "candidate"},
                {"{\"candidate\":{\"start\":\"2026-11-29T10:00:00+03:00\"}}", "candidate.end"},
                {"{\"candidate\":{\"start\":\"abc\",\"end\":\"2026-11-29T10:00:00+03:00\"}}", "candidate.start"},
                {"{\"candidate\":{\"start\":123,\"end\":456}}", "candidate.start"},
                {"{\"candidate\":{\"start\":\"2026-11-29T11:00:00+03:00\",\"end\":\"2026-11-29T10:00:00+03:00\"}}", "candidate.end"},
                {"{\"candidate\":{\"start\":\"2026-11-29T10:00:00+03:00\",\"end\":\"2026-11-29T10:00:00+03:00\"}}", "candidate.end"},
                {"{\"candidate\":" + ok + ",\"recruiterSlots\":\"abc\"}", "recruiterSlots"},
                {"{\"candidate\":" + ok + ",\"recruiterSlots\":[null]}", "recruiterSlots[0]"},
                {"{\"candidate\":" + ok + ",\"studentSlots\":[{\"start\":\"2026-11-29T10:30:00+03:00\"}]}", "studentSlots[0].end"},
                {"{\"candidate\":" + ok + ",\"studentSlots\":[{\"start\":\"2026-11-29T12:00:00+03:00\",\"end\":\"2026-11-29T11:00:00+03:00\"}]}", "studentSlots[0].end"}};
        List<Executable> checks = new ArrayList<>();
        for (String[] c : cases) {
            checks.add(() -> {
                EngineServer.Response r = EngineServer.post("/schedule/validate", c[0]);
                assertEquals(400, r.status(), () -> "HTTP status for " + c[0] + ": " + r.raw());
                assertEquals("validation", r.json().path("error").asText(), () -> "error for " + c[0] + ": " + r.raw());
                assertEquals(c[1], r.json().path("field").asText(), () -> "field for " + c[0] + ": " + r.raw());
            });
        }
        assertAll(checks);
    }

    @Test
    @DisplayName("TC-INT-008 / REL-02: malformed JSON is answered with HTTP 400, not 500")
    void malformedJsonIs400() {
        EngineServer.Response r = EngineServer.post("/schedule/validate", "{\"candidate\":");
        assertEquals(400, r.status(), () -> "HTTP status: " + r.raw());
    }

    // ------------------------------------------------------------------ TC-INT-009 offsets

    @Test
    @DisplayName("TC-INT-009 / INT-02: the same instant written with another offset gives the same answer")
    void sameInstantInAnotherOffsetGivesTheSameAnswer() {
        ObjectNode qatar = slot("2026-11-29T10:00:00+03:00", "2026-11-29T11:00:00+03:00");
        ObjectNode utc = slot("2026-11-29T07:00:00Z", "2026-11-29T08:00:00Z");
        assertEquals(validateCandidate(qatar).get("valid").asBoolean(), validateCandidate(utc).get("valid").asBoolean(), "valid slot in both offsets");
        // a recruiter slot written in UTC must still be found when it overlaps a candidate written in Qatar time
        JsonNode result = validate(body(qatar, List.of(slot("2026-11-29T07:30:00Z", "2026-11-29T08:30:00Z")), List.of()));
        assertFalse(result.get("valid").asBoolean(), () -> "overlap across offsets must be detected: " + result);
    }
}
