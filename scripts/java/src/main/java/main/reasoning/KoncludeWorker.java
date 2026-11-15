package main.reasoning;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.OWLXMLDocumentFormat;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.semanticweb.owlapi.profiles.OWL2DLProfile;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/** A supervised worker: flatten once, add conservative query definitions, then classify together. */
public final class KoncludeWorker {
    private KoncludeWorker() {}
    public static void main(String[] args) throws Exception {
        ReasoningReport report;
        try {
            report = evaluate(Path.of(args[0]), args[1].equals("-") ? null : Path.of(args[1]),
                    Path.of(args[2]), Path.of(args[3]), Duration.ofMillis(Long.parseLong(args[4])));
        } catch (Exception e) { report = ReasoningReport.toolError("Konclude load/execution failure: " + e); }
        System.out.print(new ObjectMapper().writeValueAsString(HermitWorker.protocol(report)));
    }

    public static ReasoningReport evaluate(Path input, Path catalog, Path binary, Path output, Duration timeout) throws Exception {
        KoncludeInstallation.verify(binary);
        var source = LocalReasoningOntology.load(input, catalog);
        var manager = OWLManager.createOWLOntologyManager();
        var ontology = manager.createOntology(source.axioms(Imports.INCLUDED)
                .filter(axiom -> !axiom.isAnnotationAxiom())
                .filter(axiom -> !(axiom instanceof OWLDeclarationAxiom declaration && declaration.getEntity().isOWLAnnotationProperty()))
                .map(axiom -> axiom.getAxiomWithoutAnnotations()));
        var work = Files.createTempDirectory(output, "native-");
        var json = new ObjectMapper().writerWithDefaultPrettyPrinter();
        int normalized = KoncludeNormalizer.normalize(ontology);
        json.writeValue(work.resolve("normalization.json").toFile(), Map.of(
                "rule", "ObjectMinCardinality(1,p,C) -> ObjectSomeValuesFrom(p,C)",
                "axiomReplacements", normalized,
                "annotationAxiomsRemoved", source.axioms(Imports.INCLUDED).filter(OWLAxiom::isAnnotationAxiom).count(),
                "annotationDeclarationsRemoved", source.axioms(AxiomType.DECLARATION, Imports.INCLUDED)
                        .filter(declaration -> declaration.getEntity().isOWLAnnotationProperty()).count(),
                "annotatedAxiomsStripped", source.axioms(Imports.INCLUDED).filter(OWLAxiom::isAnnotated).count()));
        if (ontology.axioms(AxiomType.SWRL_RULE).findAny().isPresent())
            throw new IllegalArgumentException("SWRL rules are not supported by this Konclude adapter");
        var df = manager.getOWLDataFactory();
        var classes = source.classesInSignature(Imports.INCLUDED).map(c -> c.getIRI().toString()).collect(Collectors.toSet());
        var objectQueries = new TreeMap<String, String>();
        var dataQueries = new TreeMap<String, String>();
        var signature = source.signature(Imports.INCLUDED).map(e -> e.getIRI().toString()).collect(Collectors.toSet());
        String prefix;
        do { prefix = "urn:soma:konclude-query:" + UUID.randomUUID() + ":"; }
        while (signature.stream().anyMatch(new QueryPrefix(prefix)));
        int index = 0;
        for (var property : source.objectPropertiesInSignature(Imports.INCLUDED).sorted().toList()) {
            if (property.isOWLBottomObjectProperty()) continue;
            var query = df.getOWLClass(IRI.create(prefix + index++));
            objectQueries.put(query.getIRI().toString(), property.getIRI().toString());
            manager.addAxiom(ontology, df.getOWLEquivalentClassesAxiom(query, df.getOWLObjectSomeValuesFrom(property, df.getOWLThing())));
        }
        for (var property : source.dataPropertiesInSignature(Imports.INCLUDED).sorted().toList()) {
            if (property.isOWLBottomDataProperty()) continue;
            var query = df.getOWLClass(IRI.create(prefix + index++));
            dataQueries.put(query.getIRI().toString(), property.getIRI().toString());
            manager.addAxiom(ontology, df.getOWLEquivalentClassesAxiom(query, df.getOWLDataSomeValuesFrom(property, df.getTopDatatype())));
        }
        // Declaration-only entities are part of the tested signature as well.
        for (var entity : ontology.signature().toList()) manager.addAxiom(ontology, df.getOWLDeclarationAxiom(entity));
        var profile = new OWL2DLProfile().checkOntology(ontology);
        if (!profile.isInProfile())
            throw new IllegalArgumentException("Outside supported OWL 2 DL profile: " + profile.getViolations());
        var flattened = work.resolve("input.owl.xml");
        // The Functional Syntax reader silently ignores some unsupported axioms (e.g. DatatypeDefinition).
        // OWL/XML reports them as errors; reject these logs below instead of claiming a complete run.
        manager.saveOntology(ontology, new OWLXMLDocumentFormat(), IRI.create(flattened.toUri()));
        json.writeValue(work.resolve("query-map.json").toFile(), Map.of("objectProperties", objectQueries, "dataProperties", dataQueries));
        // Consistency first; the query definitions are a conservative extension.
        var consistency = work.resolve("consistency.txt");
        invoke(binary, "consistency", flattened, consistency, timeout, work);
        String value = Files.readString(consistency).strip();
        if (value.equals("false")) return new ReasoningReport(true, false, List.of(), List.of(), List.of(), List.of(), List.of());
        if (!value.equals("true")) throw new IllegalArgumentException("Invalid/missing consistency boolean");
        var taxonomy = work.resolve("taxonomy.xml");
        invoke(binary, "classification", flattened, taxonomy, timeout, work);
        var expected = new HashSet<>(classes);
        expected.addAll(objectQueries.keySet());
        expected.addAll(dataQueries.keySet());
        var bottom = KoncludeXml.bottomClasses(Files.readString(taxonomy), expected);
        return new ReasoningReport(true, true, List.of(), bottom.stream().filter(classes::contains).toList(),
                objectQueries.entrySet().stream().filter(e -> bottom.contains(e.getKey())).map(Map.Entry::getValue).toList(),
                dataQueries.entrySet().stream().filter(e -> bottom.contains(e.getKey())).map(Map.Entry::getValue).toList(), List.of());
    }

    private record QueryPrefix(String prefix) implements java.util.function.Predicate<String> {
        public boolean test(String value) { return value.startsWith(prefix); }
    }

    static void invoke(Path binary, String mode, Path input, Path result, Duration timeout, Path logs) throws Exception {
        if (timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("Positive timeout required");
        var command = List.of(binary.toAbsolutePath().toString(), mode, "-w", "2", "-i", input.toAbsolutePath().toString(),
                "-o", result.toAbsolutePath().toString());
        execute(command, mode, result, timeout, logs);
    }

    static void execute(List<String> command, String mode, Path result, Duration timeout, Path logs) throws Exception {
        if (timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("Positive timeout required");
        new ObjectMapper().writeValue(logs.resolve(mode + "-command.json").toFile(), command);
        var process = new ProcessBuilder(command).redirectOutput(logs.resolve(mode + ".stdout").toFile())
                .redirectError(logs.resolve(mode + ".stderr").toFile()).start();
        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) throw new IllegalStateException("Native reasoner timeout");
            Files.writeString(logs.resolve(mode + "-exit.txt"), Integer.toString(process.exitValue()));
            if (process.exitValue() != 0) throw new IllegalStateException("Native reasoner exit code " + process.exitValue());
            for (String stream : List.of(".stdout", ".stderr")) {
                String text = Files.readString(logs.resolve(mode + stream)).toLowerCase(Locale.ROOT);
                if (text.lines().map(String::stripLeading).anyMatch(line -> line.startsWith("{error}")
                        || line.startsWith("{warning}") || line.startsWith("{warn}")))
                    throw new IllegalStateException("Native reasoner reported an error/warning; see " + logs.resolve(mode + stream));
            }
            if (!Files.isRegularFile(result) || Files.size(result) == 0) throw new IllegalStateException("Missing/empty native output");
        } finally {
            if (process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                process.waitFor();
            }
        }
    }
}
