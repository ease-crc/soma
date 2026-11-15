package main;

import main.config.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.semanticweb.owlapi.model.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RepositoryExportTest {
    @TempDir Path temp;

    @Test void fullRepositoryExportKeepsSourcesAndReloadsEveryModule() throws Exception {
        var baseline = ConfigLoader.load(Path.of("application.yaml"));
        var config = new OntologyConfig(baseline.directory(), baseline.toCollapse(), baseline.format(), temp.resolve("dist"), baseline.transformations());
        var bytes = new TreeMap<Path, byte[]>();
        try (var files = Files.list(config.directory())) {
            for (var path : files.toList()) if (Files.isRegularFile(path)) bytes.put(path, Files.readAllBytes(path));
        }
        var result = CIRunner.run(config, "test");
        for (var item : bytes.entrySet()) assertArrayEquals(item.getValue(), Files.readAllBytes(item.getKey()), item.getKey().toString());
        assertEquals(16, result.sources().documents().size());
        assertEquals(result.sources().documents().keySet(), result.exports().keySet());
        // Check that copied imports retain the complete source closure, including the ALL/All alias.
        for (var entry : result.sources().documents().entrySet()) {
            assertEquals(entry.getValue().getImportsClosure().size(),
                result.working().documents().get(entry.getKey()).getImportsClosure().size(), entry.getKey());
        }
        for (var request : config.toCollapse()) {
            var root = result.working().resolve(request.ontology());
            var excluded = new HashSet<OWLOntology>();
            for (var reference : request.except()) excluded.addAll(result.working().resolve(reference).getImportsClosure());
            var expected = new HashSet<OWLAxiom>();
            root.getImportsClosure().stream().filter(o -> !excluded.contains(o)).forEach(o -> expected.addAll(o.getAxioms()));
            assertEquals(expected, result.exports().get(request.outPath().toString()).getAxioms(), request.ontology());
        }
        var reload = new OntologyManager();
        reload.load(new OntologyConfig(config.owlDirectory(), List.of(), config.format(), temp.resolve("unused"), baseline.transformations()));
        assertEquals(16, reload.documents().size());
        var sourcePlan = main.reasoning.ReasoningSuitePlan.select(result.sources().documents(), List.of("SOMA-ALL.owl", "SOMA-HOME.owl"));
        var exportPlan = main.reasoning.ReasoningSuitePlan.select(reload.documents(), List.of("SOMA.owl", "SOMA-HOME.owl"));
        assertEquals(Set.of("SOMA-ALL.owl", "SOMA-HOME.owl", "SOMA-ELAN.owl", "SOMA-Allen.owl", "USD.owl"), Set.copyOf(sourcePlan.roots()));
        assertEquals(Set.of("SOMA.owl", "SOMA-HOME.owl", "SOMA-ELAN.owl", "SOMA-Allen.owl", "USD.owl"), Set.copyOf(exportPlan.roots()));
        assertEquals("SOMA-ALL.owl", sourcePlan.coveredBy().get("SOMA-ACT.owl"));
        assertEquals("SOMA.owl", exportPlan.coveredBy().get("SOMA-ACT.owl"));
        assertEquals(16, sourcePlan.coveredBy().size());
        assertEquals(16, exportPlan.coveredBy().size());
        var home = reload.resolve("http://www.ease-crc.org/ont/SOMA-HOME.owl");
        var soma = reload.resolve("http://www.ease-crc.org/ont/SOMA.owl");
        assertTrue(home.getImportsClosure().contains(soma));
        assertEquals(result.exports().get("SOMA.owl").getLogicalAxioms(), soma.getLogicalAxioms());
        assertTrue(soma.getAxiomCount() > result.sources().resolve("http://www.ease-crc.org/ont/SOMA.owl").getAxiomCount());
        var reasonerInput = org.semanticweb.owlapi.apibinding.OWLManager.createOWLOntologyManager()
            .loadOntologyFromOntologyDocument(config.outputDirectory().resolve("work/SOMA-HOME.owl").toFile());
        assertTrue(reasonerInput.getImportsDeclarations().isEmpty());
        assertEquals(home.axioms(org.semanticweb.owlapi.model.parameters.Imports.INCLUDED).collect(java.util.stream.Collectors.toSet()),
            reasonerInput.getAxioms());
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        var manifest = json.readTree(config.outputDirectory().resolve("manifest.json").toFile());
        assertEquals("test", manifest.get("version").asText());
        assertEquals(17, manifest.get("sha256").size());
        for (var entry : manifest.get("sha256").properties()) {
            var actual = java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(config.outputDirectory().resolve(entry.getKey())));
            assertEquals(entry.getValue().asText(), HexFormat.of().formatHex(actual));
        }
        for (var item : result.sources().documents().entrySet()) {
            assertFalse(item.getValue().annotations().anyMatch(a -> a.getValue().equals(
                result.sources().getOntologyManager().getOWLDataFactory().getOWLLiteral("test"))));
        }
    }
}
