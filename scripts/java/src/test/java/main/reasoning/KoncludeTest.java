package main.reasoning;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.profiles.OWL2DLProfile;
import static org.junit.jupiter.api.Assertions.*;

class KoncludeTest {
    @TempDir Path temp;
    private static final String OWL = "http://www.w3.org/2002/07/owl#";
    private static final String TAXONOMY = """
        <o:Ontology xmlns:o="http://www.w3.org/2002/07/owl#">
          <o:Declaration><o:Class IRI="http://www.w3.org/2002/07/owl#Thing"/></o:Declaration>
          <o:Declaration><o:Class IRI="http://www.w3.org/2002/07/owl#Nothing"/></o:Declaration>
          <o:Declaration><o:Class IRI="urn:empty"/></o:Declaration>
          <o:Declaration><o:Class IRI="urn:indirect"/></o:Declaration>
          <o:EquivalentClasses><o:Class IRI="urn:empty"/><o:Class IRI="http://www.w3.org/2002/07/owl#Nothing"/></o:EquivalentClasses>
          <o:SubClassOf><o:Class IRI="urn:indirect"/><o:Class IRI="urn:empty"/></o:SubClassOf>
        </o:Ontology>
        """;

    @Test void parsesNamespacesWhitespaceAndSubclassPaths() throws Exception {
        var expected = Set.of("urn:empty", "urn:indirect");
        assertEquals(expected, KoncludeXml.bottomClasses(TAXONOMY, expected));
        assertEquals(expected, KoncludeXml.bottomClasses(TAXONOMY.replace("\n", ""), expected));
    }

    @Test void incompleteInvalidForeignAndExternalXmlCannotPass() {
        for (String xml : List.of("", "garbage", "<Ontology/>", TAXONOMY.substring(0, TAXONOMY.length() / 2),
                TAXONOMY.replace("urn:indirect", "urn:other"),
                TAXONOMY.replace("<o:SubClassOf><o:Class IRI=\"urn:indirect\"/><o:Class IRI=\"urn:empty\"/></o:SubClassOf>", ""),
                TAXONOMY.replace("<o:SubClassOf>", "<o:Unknown>").replace("</o:SubClassOf>", "</o:Unknown>"),
                "<!DOCTYPE a [<!ENTITY x SYSTEM 'file:///no-network'>]>" + TAXONOMY)) {
            assertThrows(Exception.class, () -> KoncludeXml.bottomClasses(xml, Set.of("urn:indirect")), xml);
        }
        assertThrows(Exception.class, () -> KoncludeXml.bottomClasses(TAXONOMY.replace("urn:empty", OWL + "Thing"), Set.of()));
    }

    @Test void binaryMustExistAndMatchOfficialRelease() throws Exception {
        assertThrows(Exception.class, () -> KoncludeInstallation.verify(temp.resolve("absent")));
        var fake = temp.resolve("Konclude.exe");
        Files.writeString(fake, "not a reasoner");
        fake.toFile().setExecutable(true);
        assertThrows(Exception.class, () -> KoncludeInstallation.verify(fake));
        assertEquals(2, HermitCli.run(temp.resolve("input"), null, temp.resolve("logs"), Duration.ofSeconds(1), 128, fake).exitCode());
        assertTrue(Files.readString(temp.resolve("logs/report.json")).contains("TOOL_ERROR"));
    }

    @Test void normalizesNestedMinimumOneAndPreservesOtherCardinalitiesAndOriginal() throws Exception {
        var manager = OWLManager.createOWLOntologyManager();
        var df = manager.getOWLDataFactory();
        var property = df.getOWLObjectProperty(IRI.create("urn:p"));
        var named = df.getOWLClass(IRI.create("urn:A"));
        var original = manager.createOntology(Set.of(df.getOWLTransitiveObjectPropertyAxiom(property),
                df.getOWLDeclarationAxiom(property), df.getOWLDeclarationAxiom(named),
                df.getOWLSubClassOfAxiom(named, df.getOWLObjectMinCardinality(1, property,
                        df.getOWLObjectMinCardinality(1, property.getInverseProperty(), df.getOWLThing())))));
        var copy = manager.createOntology(original.axioms());
        assertTrue(KoncludeNormalizer.normalize(copy) > 0);
        var expected = df.getOWLSubClassOfAxiom(named, df.getOWLObjectSomeValuesFrom(property,
                df.getOWLObjectSomeValuesFrom(property.getInverseProperty(), df.getOWLThing())));
        assertTrue(copy.containsAxiom(expected));
        assertFalse(original.containsAxiom(expected));
        assertTrue(new OWL2DLProfile().checkOntology(copy).isInProfile());
        assertEquals(0, KoncludeNormalizer.normalize(copy));
        manager.addAxiom(copy, df.getOWLSubClassOfAxiom(named, df.getOWLObjectMinCardinality(2, property)));
        assertEquals(0, KoncludeNormalizer.normalize(copy));
        assertFalse(new OWL2DLProfile().checkOntology(copy).isInProfile());
    }

    void invoke(String mode, Duration timeout) throws Exception {
        var result = temp.resolve(mode + "-result");
        KoncludeWorker.execute(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-cp",
                System.getProperty("java.class.path"), NativeStub.class.getName(), mode, result.toString()),
                mode, result, timeout, temp);
    }

    @Test void failedNativeProcessAndLaunchAreErrors() throws Exception {
        invoke("pass", Duration.ofSeconds(10));
        for (String mode : List.of("abort", "empty", "error", "warning"))
            assertThrows(Exception.class, () -> invoke(mode, Duration.ofSeconds(10)), mode);
        assertThrows(Exception.class, () -> invoke("timeout", Duration.ofSeconds(2)));
        assertThrows(Exception.class, () -> KoncludeWorker.invoke(temp.resolve("missing"), "consistency", temp,
                temp.resolve("out"), Duration.ofSeconds(1), temp));
        assertThrows(IllegalArgumentException.class, () -> invoke("none", Duration.ZERO));
    }

    public static class NativeStub {
        public static void main(String[] args) throws Exception {
            String mode = args[0];
            System.out.println("diagnostic");
            System.out.println("{info} path containing {error} is valid");
            if (!mode.equals("empty")) Files.writeString(Path.of(args[1]), "true");
            if (mode.equals("abort")) System.exit(7);
            if (mode.equals("error")) System.out.println("{error} skipped axiom");
            if (mode.equals("warning")) System.err.println("{warning} ignored axiom");
            if (mode.equals("timeout")) Thread.sleep(60000);
        }
    }
}
