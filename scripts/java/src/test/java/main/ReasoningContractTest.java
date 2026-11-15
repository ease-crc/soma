package main;

import main.reasoning.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Duration;
import java.util.List;
import static main.reasoning.ReasoningReport.Status.*;
import static org.junit.jupiter.api.Assertions.*;

class ReasoningContractTest {
    @TempDir Path temp;
    static final String PASS_JSON = """
        {"schemaVersion":1,"complete":true,"consistent":true,"toolErrors":[],
         "unsatisfiableClasses":[],"emptyObjectProperties":[],"emptyDataProperties":[],"qualityFindings":[]}
        """;

    ReasoningReport evaluate(String fixture) {
        try {
            var path = Path.of(getClass().getResource("/reasoning/" + fixture + ".ofn").toURI());
            return HermitEvaluator.evaluate(path, path.resolveSibling("catalog-v001.xml"));
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Test void ontologyFixturesHaveExactExpectedResults() {
        assertEquals(new ReasoningReport(true, true, List.of(), List.of(), List.of(), List.of(), List.of()), evaluate("consistent"));
        assertEquals(PASS, evaluate("consistent").status());
        assertEquals(new ReasoningReport(true, false, List.of(), List.of(), List.of(), List.of(), List.of()), evaluate("inconsistent"));
        assertEquals(INCONSISTENT, evaluate("inconsistent").status());
        assertEquals(new ReasoningReport(true, true, List.of(), List.of("urn:b1:Impossible"), List.of(), List.of(), List.of()), evaluate("unsatisfiable-class"));
        assertEquals(new ReasoningReport(true, true, List.of(), List.of(), List.of("urn:b1:emptyObject"), List.of(), List.of()), evaluate("empty-object-property"));
        assertEquals(new ReasoningReport(true, true, List.of(), List.of(), List.of(), List.of("urn:b1:emptyData"), List.of()), evaluate("empty-data-property"));
        for (String fixture : List.of("unsatisfiable-class", "empty-object-property", "empty-data-property")) {
            assertEquals(FINDINGS, evaluate(fixture).status());
            assertEquals(1, evaluate(fixture).exitCode());
        }
        var missing = evaluate("missing-import");
        assertEquals(TOOL_ERROR, missing.status());
        assertTrue(missing.toolErrors().getFirst().contains("Missing local catalog mapping"), missing.toString());
    }

    @Test void builtinsAreRemovedButOtherFindingsArePreservedAndSorted() {
        String owl = "http://www.w3.org/2002/07/owl#";
        var report = new ReasoningReport(true, true, List.of(), List.of(owl + "Nothing", "urn:z", "urn:a", "urn:z"),
                List.of(owl + "bottomObjectProperty", "urn:p"), List.of(owl + "bottomDataProperty", "urn:d"), List.of("quality"));
        assertEquals(List.of("urn:a", "urn:z"), report.unsatisfiableClasses());
        assertEquals(List.of("urn:p"), report.emptyObjectProperties());
        assertEquals(List.of("urn:d"), report.emptyDataProperties());
        assertEquals(List.of("quality"), report.qualityFindings());
        assertEquals(FINDINGS, new ReasoningReport(true, true, List.of(), List.of(), List.of(), List.of(), List.of("quality")).status());
        assertEquals(TOOL_ERROR, new ReasoningReport(true, true, List.of("tool failed"), List.of(), List.of(), List.of(), List.of()).status());
    }

    @Test void invalidOrIncompleteOutputCannotPass() {
        assertEquals(0, ReasoningProtocol.decode(PASS_JSON).exitCode());
        for (String output : List.of("", " ", "garbage", "{}", "[]", "null", PASS_JSON + "{}",
                PASS_JSON.replace("\"schemaVersion\":1", "\"schemaVersion\":2"),
                PASS_JSON.replace("\"complete\":true", "\"complete\":false"),
                PASS_JSON.replace("\"complete\":true", "\"complete\":true,\"complete\":true"),
                PASS_JSON.replace("\"consistent\":true", "\"consistent\":null"),
                PASS_JSON.replace("\"emptyDataProperties\":[],", ""),
                PASS_JSON.replace("\"emptyDataProperties\":[]", "\"emptyDataProperties\":null"),
                PASS_JSON.replace("\"qualityFindings\":[]", "\"qualityFindings\":[17]"))) {
            assertEquals(TOOL_ERROR, ReasoningProtocol.decode(output).status(), output);
            assertEquals(2, ReasoningProtocol.decode(output).exitCode(), output);
        }
        assertThrows(IllegalArgumentException.class, () -> new ReasoningReport(true, false, List.of(),
                List.of("urn:A"), List.of(), List.of(), List.of()));
    }

    ReasoningReport process(String mode, Duration timeout) throws Exception {
        return ReasoningProcess.run(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"), Worker.class.getName(), mode), timeout, temp);
    }

    @Test void processFailuresOverrideEvenValidSuccessOutput() throws Exception {
        assertEquals(PASS, process("pass", Duration.ofSeconds(15)).status());
        for (String mode : List.of("empty", "invalid", "abort"))
            assertEquals(TOOL_ERROR, process(mode, Duration.ofSeconds(15)).status(), mode);
        assertTrue(process("timeout", Duration.ofSeconds(2)).toolErrors().getFirst().contains("timeout"));
        assertEquals(TOOL_ERROR, ReasoningProcess.run(List.of(temp.resolve("absent-binary").toString()),
                Duration.ofSeconds(1), temp).status());
        try (var logs = Files.list(temp)) {
            assertTrue(logs.anyMatch(p -> {
                try { return p.toString().endsWith(".stderr") && Files.readString(p).contains("diagnostic"); }
                catch (Exception e) { throw new RuntimeException(e); }
            }));
        }
    }

    public static class Worker {
        public static void main(String[] args) throws Exception {
            System.err.println("diagnostic");
            switch (args[0]) {
                case "pass" -> System.out.print(PASS_JSON);
                case "invalid" -> System.out.print("invalid");
                case "empty" -> { }
                case "abort" -> { System.out.print(PASS_JSON); System.exit(7); }
                case "timeout" -> { System.out.print(PASS_JSON); System.out.flush(); Thread.sleep(60000); }
                default -> throw new IllegalArgumentException(args[0]);
            }
        }
    }
}
