package main.reasoning;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Set KONCLUDE_BINARY to run the real, pinned native contract (required in the Konclude CI job). */
class KoncludeIntegrationTest {
    @TempDir Path temp;
    Path binary() {
        var value = System.getenv("KONCLUDE_BINARY");
        Assumptions.assumeTrue(value != null && !value.isBlank(), "Set KONCLUDE_BINARY for native integration tests");
        return Path.of(value);
    }
    Path fixture(String name) throws Exception {
        return Path.of(getClass().getResource("/reasoning/" + name + ".ofn").toURI());
    }
    ReasoningReport run(Path input, Path catalog, String name) throws Exception {
        return HermitCli.run(input, catalog, temp.resolve(name), Duration.ofSeconds(120), 256, binary());
    }

    @Test void allB1FixturesMatchHermitExactly() throws Exception {
        for (String name : List.of("consistent", "inconsistent", "unsatisfiable-class", "empty-object-property", "empty-data-property")) {
            var input = fixture(name);
            assertEquals(HermitEvaluator.evaluate(input, null), run(input, null, name), name);
        }
        assertEquals(ReasoningReport.Status.TOOL_ERROR, run(fixture("missing-import"), null, "missing").status());
    }

    @Test void closureAndUndeclaredPropertiesComeFromTheTestedInput() throws Exception {
        var nativeBinary = binary();
        var folder = Files.createDirectories(temp.resolve("directory with spaces"));
        Files.copy(fixture("empty-object-property"), folder.resolve("import.ofn"));
        var root = folder.resolve("root.ofn");
        Files.writeString(root, """
            Ontology(<urn:root> Import(<urn:b1:empty-object-property>)
              ObjectPropertyDomain(<urn:undeclared> <http://www.w3.org/2002/07/owl#Nothing>))
            """);
        var catalog = folder.resolve("catalog-v001.xml");
        Files.writeString(catalog, """
            <catalog xmlns="urn:oasis:names:tc:entity:xmlns:xml:catalog">
              <uri name="urn:b1:empty-object-property" uri="import.ofn"/>
            </catalog>
            """);
        var report = run(root, catalog, "closure");
        assertEquals(List.of("urn:b1:emptyObject", "urn:undeclared"), report.emptyObjectProperties());
        assertEquals(HermitEvaluator.evaluate(root, catalog), report);
        try (var paths = Files.walk(temp.resolve("closure"))) {
            var flat = paths.filter(p -> p.endsWith("input.owl.xml")).findFirst().orElseThrow();
            assertFalse(Files.readString(flat).contains("<Import>"));
            assertTrue(Files.readString(flat.resolveSibling("query-map.json")).contains("urn:undeclared"));
        }
        var json = new ObjectMapper();
        assertEquals("Konclude", json.readTree(temp.resolve("closure/report.json").toFile()).at("/metadata/reasoner").asText());
        assertEquals(ReasoningReport.Status.TOOL_ERROR, HermitCli.run(root, catalog, temp.resolve("timeout"),
                Duration.ofMillis(1), 256, nativeBinary).status());
    }

    @Test void unsupportedDatatypeDefinitionsMustNeverPass() throws Exception {
        binary();
        var input = temp.resolve("datatype.ofn");
        Files.writeString(input, """
            Ontology(<urn:unsupported>
              DatatypeDefinition(<urn:emptyDT> DataIntersectionOf(<http://www.w3.org/2001/XMLSchema#string> <http://www.w3.org/2001/XMLSchema#integer>))
              DataPropertyRange(<urn:p> <urn:emptyDT>))
            """);
        assertEquals(List.of("urn:p"), HermitEvaluator.evaluate(input, null).emptyDataProperties());
        assertEquals(ReasoningReport.Status.TOOL_ERROR, run(input, null, "unsupported").status());
    }

    @Test void nonSimpleRoleRestrictionsMustNeverPass() throws Exception {
        binary();
        var input = temp.resolve("outside-dl.ofn");
        Files.writeString(input, """
            Ontology(<urn:outside-dl> TransitiveObjectProperty(<urn:p>) AsymmetricObjectProperty(<urn:p>))
            """);
        assertEquals(ReasoningReport.Status.TOOL_ERROR, run(input, null, "outside-dl").status());
    }

    @Test void swrlRulesCannotBeSilentlyMisinterpreted() throws Exception {
        binary();
        var input = temp.resolve("rule.ofn");
        Files.writeString(input, """
            Ontology(<urn:rule>
              ClassAssertion(<urn:A> <urn:a>)
              DLSafeRule(Body(ClassAtom(<urn:A> Variable(<urn:x>))
                BuiltInAtom(<http://www.w3.org/2003/11/swrlb#equal> "1"^^<http://www.w3.org/2001/XMLSchema#integer> "2"^^<http://www.w3.org/2001/XMLSchema#integer>))
                Head(ClassAtom(<http://www.w3.org/2002/07/owl#Nothing> Variable(<urn:x>)))))
            """);
        var report = run(input, null, "rule");
        assertEquals(ReasoningReport.Status.TOOL_ERROR, report.status());
        assertTrue(report.toolErrors().getFirst().contains("SWRL"));
    }

    @Test void annotationsDoNotPreventLogicalChecks() throws Exception {
        binary();
        var input = temp.resolve("annotated.ofn");
        Files.writeString(input, """
            Ontology(<urn:annotated> Declaration(Class(<urn:A>))
              Declaration(AnnotationProperty(<http://www.w3.org/2000/01/rdf-schema#label>))
              AnnotationAssertion(<http://www.w3.org/2000/01/rdf-schema#label> <urn:A> "A label")
              SubClassOf(Annotation(<http://www.w3.org/2000/01/rdf-schema#comment> "logical axiom") <urn:A> <http://www.w3.org/2002/07/owl#Nothing>))
            """);
        assertEquals(HermitEvaluator.evaluate(input, null), run(input, null, "annotated"));
    }
}
