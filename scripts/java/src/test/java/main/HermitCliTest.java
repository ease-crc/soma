package main;

import main.reasoning.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

class HermitCliTest {
    @TempDir Path temp;

    Path fixture(String name) throws Exception {
        return Path.of(getClass().getResource("/reasoning/" + name + ".ofn").toURI());
    }

    @Test void invalidOptionsAreRejectedBeforeCreatingOutput() {
        var output = temp.resolve("reports");
        for (String[] arguments : new String[][]{
                {}, {"--input=missing.owl", "--suite=."}, {"--suite=.", "--catalog=catalog.xml"},
                {"--input=missing.owl", "--scope=roots"}, {"--suite=.", "--scope=unknown"},
                {"--suite=.", "--reasoner=unknown"}, {"--suite=.", "--reasoner=konclude"},
                {"--suite=.", "--binary=Konclude"}, {"--suite=.", "--timeout-seconds=0"},
                {"--suite=.", "--memory-mb=63"}, {"--suite=.", "--suite=."},
                {"--suite=.", "--unknown=value"}, {"--suite="}, {"--suite"}}) {
            var options = new java.util.ArrayList<>(java.util.List.of(arguments));
            options.add("--output=" + output);
            assertThrows(IllegalArgumentException.class, () -> HermitCli.execute(options.toArray(String[]::new)),
                    options.toString());
            assertFalse(Files.exists(output));
        }
    }

    @Test void subprocessPreservesSemanticResultsAndRecordsIdentity() throws Exception {
        var input = fixture("empty-data-property");
        var output = temp.resolve("report");
        assertEquals(1, HermitCli.execute(new String[]{"--input=" + input, "--output=" + output}));
        var document = new ObjectMapper().readTree(output.resolve("input/report.json").toFile());
        assertEquals("FINDINGS", document.path("status").asText());
        assertEquals("urn:b1:emptyData", document.at("/result/emptyDataProperties/0").asText());
        assertEquals("1.4.5.519", document.at("/metadata/reasonerVersion").asText());
        assertEquals(2048, document.at("/metadata/memoryLimitMiB").asInt());
        assertTrue(document.at("/metadata/durationMillis").asLong() > 0);
        assertEquals(64, document.at("/metadata/inputFilesSha256").get(input.toRealPath().toString()).asText().length());
    }

    @Test void timeoutAndMissingInputProduceExplicitToolReports() throws Exception {
        assertEquals(ReasoningReport.Status.TOOL_ERROR, HermitCli.run(fixture("consistent"), null,
                temp.resolve("timeout"), Duration.ofMillis(1), 128).status());
        assertEquals(2, HermitCli.execute(new String[]{"--input=" + temp.resolve("missing.owl"), "--output=" + temp.resolve("missing")}));
        var document = new ObjectMapper().readTree(temp.resolve("missing/input/report.json").toFile());
        assertEquals("TOOL_ERROR", document.path("status").asText());
        assertFalse(document.at("/result/complete").asBoolean());
    }

    @Test void suiteChecksEveryEntryPointAndContinuesAfterFindingsAndToolFailures() throws Exception {
        for (String directory : new String[]{"owl", "build/ci/dist/owl"}) {
            var dir = Files.createDirectories(temp.resolve(directory));
            Files.copy(fixture("consistent"), dir.resolve("first.owl"));
            Files.copy(fixture("inconsistent"), dir.resolve("second.owl"));
            Files.copy(fixture("missing-import"), dir.resolve("third.owl"));
            Files.writeString(dir.resolve("catalog-v001.xml"), "<catalog xmlns=\"urn:oasis:names:tc:entity:xmlns:xml:catalog\"/>");
        }
        var output = temp.resolve("reports");
        assertEquals(2, HermitCli.execute(new String[]{"--suite=" + temp, "--scope=modules", "--output=" + output, "--memory-mb=128"}));
        var summary = new ObjectMapper().readTree(output.resolve("summary.json").toFile());
        assertEquals(6, summary.path("entries").size());
        for (int offset : new int[]{0, 3}) {
            assertEquals("PASS", summary.at("/entries/" + offset + "/status").asText());
            assertEquals("INCONSISTENT", summary.at("/entries/" + (offset + 1) + "/status").asText());
            assertEquals("TOOL_ERROR", summary.at("/entries/" + (offset + 2) + "/status").asText());
        }
    }

    @Test void defaultSuiteReasonsOverCoveringRootsAndRecordsModuleCoverage() throws Exception {
        for (String directory : new String[]{"owl", "build/ci/dist/owl"}) {
            var dir = Files.createDirectories(temp.resolve(directory));
            Files.copy(fixture("consistent"), dir.resolve("Independent.owl"));
            Files.copy(fixture("unsatisfiable-class"), dir.resolve("Module.owl"));
            Files.writeString(dir.resolve("Root.owl"), """
                    Ontology(<urn:plan:root> Import(<urn:b1:unsatisfiable-class>)
                      SubClassOf(<urn:plan:RootOnly> <http://www.w3.org/2002/07/owl#Thing>))
                    """);
            Files.writeString(dir.resolve("catalog-v001.xml"), """
                    <catalog xmlns="urn:oasis:names:tc:entity:xmlns:xml:catalog">
                      <uri name="urn:b1:unsatisfiable-class" uri="Module.owl"/>
                    </catalog>
                    """);
        }
        var output = temp.resolve("reports");
        assertEquals(1, HermitCli.execute(new String[]{"--suite=" + temp, "--output=" + output, "--memory-mb=128"}));
        var json = new ObjectMapper();
        var summary = json.readTree(output.resolve("summary.json").toFile());
        var coverage = json.readTree(output.resolve("coverage.json").toFile());
        assertEquals(4, summary.path("entries").size());
        assertEquals("Root.owl", coverage.at("/sources/coveredBy/Module.owl").asText());
        assertEquals("Root.owl", coverage.at("/exports/coveredBy/Module.owl").asText());
        assertEquals(2, coverage.at("/sources/roots").size());
    }

    @Test void importedSignaturesAndCachedImportsAreChecked() throws Exception {
        var imported = temp.resolve("imported.ofn");
        Files.copy(fixture("empty-object-property"), imported);
        var root = temp.resolve("root.ofn");
        Files.writeString(root, "Ontology(<urn:test:root> Import(<urn:b1:empty-object-property>))");
        var catalog = temp.resolve("catalog-v001.xml");
        Files.writeString(catalog, """
                <catalog xmlns="urn:oasis:names:tc:entity:xmlns:xml:catalog">
                  <uri name="urn:b1:empty-object-property" uri="imported.ofn"/>
                </catalog>
                """);
        assertEquals(java.util.List.of("urn:b1:emptyObject"), HermitEvaluator.evaluate(root, catalog).emptyObjectProperties());
        // An import of the root's already loaded ontology IRI must still be mapped locally.
        Files.writeString(root, "Ontology(<urn:test:root> Import(<urn:test:root>))");
        assertEquals(ReasoningReport.Status.TOOL_ERROR, HermitEvaluator.evaluate(root, catalog).status());
    }

    @Test void failedSuiteDiscoveryInvalidatesPreviousSuccess() throws Exception {
        var output = Files.createDirectories(temp.resolve("reports"));
        Files.writeString(output.resolve("summary.json"), "{\"complete\":true,\"exitCode\":0}");
        assertThrows(Exception.class, () -> HermitCli.execute(new String[]{"--suite=" + temp, "--output=" + output}));
        var summary = new ObjectMapper().readTree(output.resolve("summary.json").toFile());
        assertFalse(summary.path("complete").asBoolean());
        assertEquals(2, summary.path("exitCode").asInt());
    }
}
