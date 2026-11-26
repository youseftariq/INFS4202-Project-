package uniplace.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Black-box tests of POST /allowance (OFF-05, OFF-07, REL-02), designed in docs/test-cases.md as TC-OFF-001 to
 * TC-OFF-011. The oracle is the Requirements (OFF-05) and the OpenAPI description of /allowance, worked out by hand.
 */
class AllowanceApiTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** 3000 a month, 01/01/2027 to 31/03/2027 (3 whole months), 20 h a week, no late days, default Settings. */
    private static ObjectNode request() {
        ObjectNode r = JSON.createObjectNode();
        r.put("monthlyAllowance", 3000);
        r.put("startDate", "2027-01-01");
        r.put("endDate", "2027-03-31");
        r.put("weeklyHours", 20);
        r.put("lateDays", 0);
        r.put("transportSupplement", 300);
        r.put("monthlyCap", 5000);
        r.put("penaltyRate", 0.05);
        r.put("penaltyCap", 0.20);
        return r;
    }

    private static ObjectNode request(Consumer<ObjectNode> change) {
        ObjectNode r = request();
        change.accept(r);
        return r;
    }

    private static JsonNode ok(ObjectNode body) {
        EngineServer.Response r = EngineServer.post("/allowance", body.toString());
        assertEquals(200, r.status(), () -> "HTTP status for " + body + ": " + r.raw());
        return r.json();
    }

    private static void assertTotal(String expected, JsonNode result) {
        assertEquals(0, new BigDecimal(expected).compareTo(result.get("total").decimalValue()),
                () -> "total: expected " + expected + " but was " + result.get("total") + " in " + result);
    }

    /** The first line whose label contains the given word (ignoring case), or null. */
    private static JsonNode line(JsonNode result, String word) {
        for (JsonNode l : result.get("lines")) {
            if (l.get("label").asText().toLowerCase().contains(word.toLowerCase())) {
                return l;
            }
        }
        return null;
    }

    private static List<String> labels(JsonNode result) {
        List<String> out = new ArrayList<>();
        result.get("lines").forEach(l -> out.add(l.get("label").asText()));
        return out;
    }

    @Test
    @DisplayName("TC-OFF-001 / OFF-05: whole months with no supplement or penalty give base x months")
    void baseOnlyForWholeMonths() {
        JsonNode result = ok(request());
        assertAll(
                () -> assertTotal("9000.00", result),
                () -> assertNull(line(result, "transport"), "no transport line expected: " + labels(result)),
                () -> assertNull(line(result, "penalty"), "no penalty line expected: " + labels(result)));
    }

    @Test
    @DisplayName("TC-OFF-002 / OFF-05: the transport supplement applies at exactly 30 weekly hours")
    void transportSupplementFromThirtyHours() {
        JsonNode result = ok(request(r -> r.put("weeklyHours", 30)));
        assertAll(
                () -> assertNotNull(line(result, "transport"), "transport line expected at 30 h: " + labels(result)),
                () -> assertTotal("9900.00", result));
    }

    @ParameterizedTest(name = "{0} h a week gives {1}")
    @CsvSource({"29, 9000.00", "31, 9900.00", "40, 9900.00"})
    @DisplayName("TC-OFF-003 / OFF-05: the transport supplement around the 30-hour threshold")
    void transportSupplementAroundThreshold(int hours, String total) {
        assertTotal(total, ok(request(r -> r.put("weeklyHours", hours))));
    }

    @ParameterizedTest(name = "{0} a month with transport, cap {1}, gives {2}")
    @CsvSource({"4900, 5000, 15000.00", "5000, 5000, 15000.00", "3000, 3100, 9300.00"})
    @DisplayName("TC-OFF-004 / OFF-05: the monthly total after the supplement is capped")
    void monthlyTotalIsCapped(int monthly, int cap, String total) {
        JsonNode result = ok(request(r -> {
            r.put("monthlyAllowance", monthly);
            r.put("weeklyHours", 40);
            r.put("monthlyCap", cap);
        }));
        assertAll(
                () -> assertNotNull(line(result, "cap"), "cap adjustment line expected: " + labels(result)),
                () -> assertTotal(total, result));
    }

    @Test
    @DisplayName("TC-OFF-005 / OFF-05: a partial month counts as days divided by 30")
    void partialMonthCountsDaysOverThirty() {
        assertTotal("4500.00", ok(request(r -> r.put("endDate", "2027-02-15"))));
    }

    @ParameterizedTest(name = "{0} late days: {1}")
    @CsvSource({"1, 8550.00", "7, 8550.00", "8, 8100.00"})
    @DisplayName("TC-OFF-006 / OFF-05, OFF-07: 5% penalty per started week")
    void penaltyPerStartedWeek(int lateDays, String total) {
        assertTotal(total, ok(request(r -> r.put("lateDays", lateDays))));
    }

    @ParameterizedTest(name = "{0} late days: capped at 20%")
    @ValueSource(ints = {28, 29, 60})
    @DisplayName("TC-OFF-007 / OFF-05: the penalty is capped at 20%")
    void penaltyIsCappedAtTwentyPercent(int lateDays) {
        assertTotal("7200.00", ok(request(r -> r.put("lateDays", lateDays))));
    }

    @ParameterizedTest(name = "{0} to {1} is one month")
    @CsvSource({"2027-01-31, 2027-02-28", "2027-03-31, 2027-04-30", "2027-01-15, 2027-02-14"})
    @DisplayName("TC-OFF-008 / OFF-05: a month starting on a day the next month lacks runs to the end of that month")
    void monthStartingOnDayThirtyOne(String start, String end) {
        assertTotal("3000.00", ok(request(r -> {
            r.put("startDate", start);
            r.put("endDate", end);
        })));
    }

    @Test
    @DisplayName("TC-OFF-009 / OFF-05: the total is rounded half up to 2 decimals")
    void totalIsRoundedHalfUp() {
        assertTotal("500.02", ok(request(r -> {
            r.put("monthlyAllowance", new BigDecimal("1000.03"));
            r.put("endDate", "2027-01-15");
        })));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "{\"startDate\":\"2027-01-01\",\"endDate\":\"2027-03-31\",\"weeklyHours\":20,\"lateDays\":0}",
            "{\"monthlyAllowance\":\"abc\",\"startDate\":\"2027-01-01\",\"endDate\":\"2027-03-31\",\"weeklyHours\":20,\"lateDays\":0}",
            "{\"monthlyAllowance\":null,\"startDate\":\"2027-01-01\",\"endDate\":\"2027-03-31\",\"weeklyHours\":20,\"lateDays\":0}",
            "{}"})
    @DisplayName("TC-OFF-010 / REL-02: a missing or wrongly typed field is a 400 naming the field, never a 500")
    void missingOrWronglyTypedFieldIs400(String body) {
        EngineServer.Response r = EngineServer.post("/allowance", body);
        assertAll(
                () -> assertEquals(400, r.status(), "HTTP status: " + r.raw()),
                () -> assertEquals("validation", r.json().path("error").asText(), r.raw()),
                () -> assertTrue(r.json().hasNonNull("field"), "the body must name the invalid field: " + r.raw()));
    }

    @ParameterizedTest(name = "{0} = {1}")
    @CsvSource({
            "monthlyAllowance, 5001", "monthlyAllowance, -1",
            "weeklyHours, 9", "weeklyHours, 41", "weeklyHours, 20.5",
            "lateDays, -1"})
    @DisplayName("TC-OFF-011 / REL-02: out-of-range values are a 400 naming the field")
    void outOfRangeValuesAre400(String field, String value) {
        ObjectNode body = request(r -> r.put(field, new BigDecimal(value)));
        EngineServer.Response r = EngineServer.post("/allowance", body.toString());
        assertAll(
                () -> assertEquals(400, r.status(), "HTTP status: " + r.raw()),
                () -> assertEquals(field, r.json().path("field").asText(), r.raw()));
    }

    @Test
    @DisplayName("TC-OFF-011 / REL-02: an end date before the start date is a 400")
    void endBeforeStartIs400() {
        EngineServer.Response r = EngineServer.post("/allowance",
                request(b -> b.put("endDate", "2026-12-31")).toString());
        assertAll(
                () -> assertEquals(400, r.status(), "HTTP status: " + r.raw()),
                () -> assertEquals("endDate", r.json().path("field").asText(), r.raw()));
    }
}
