package main.reasoning;

import main.OntologyManager;
import main.config.OntologyConfig;
import main.config.TransformationConfig;
import org.semanticweb.owlapi.formats.RDFXMLDocumentFormat;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.Imports;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/** Keep public roots and add only theories not covered by any single selected root. */
public final class ReasoningSuitePlan {
    private ReasoningSuitePlan() {}

    public record Plan(List<String> roots, Map<String, String> coveredBy) {
        public Plan {
            roots = List.copyOf(roots);
            coveredBy = Collections.unmodifiableMap(new TreeMap<>(coveredBy));
        }
    }

    private record Theory(Set<OWLAxiom> axioms, Set<OWLEntity> signature) {
        boolean covers(Theory other) {
            return axioms.containsAll(other.axioms) && signature.containsAll(other.signature);
        }
    }

    public static Plan load(Path directory, Path unusedOutput, List<String> preferred) throws Exception {
        // The existing strict loader validates every document and its local imports; no writes or transformations.
        var documents = new OntologyManager();
        documents.load(new OntologyConfig(directory, List.of(), new RDFXMLDocumentFormat(), unusedOutput,
                new TransformationConfig(false, false, false, false)));
        return select(documents.documents(), preferred);
    }

    public static Plan select(Map<String, OWLOntology> documents, List<String> preferred) {
        if (documents.isEmpty()) throw new IllegalArgumentException("No ontologies to cover");
        var names = documents.keySet().stream().sorted().toList();
        var theories = new HashMap<String, Theory>();
        for (var name : names) {
            var ontology = documents.get(name);
            var axioms = ontology.axioms(Imports.INCLUDED).filter(OWLAxiom::isLogicalAxiom)
                    .<OWLAxiom>map(axiom -> axiom.getAxiomWithoutAnnotations()).collect(Collectors.toSet());
            var signature = ontology.signature(Imports.INCLUDED)
                    .filter(e -> e.isOWLClass() || e.isOWLObjectProperty() || e.isOWLDataProperty())
                    .collect(Collectors.toSet());
            theories.put(name, new Theory(axioms, signature));
        }
        var selected = new LinkedHashSet<String>();
        preferred.stream().filter(documents::containsKey).forEach(selected::add);
        while (true) {
            var remaining = names.stream().filter(name -> selected.stream()
                    .noneMatch(root -> theories.get(root).covers(theories.get(name)))).toList();
            if (remaining.isEmpty()) break;
            // Prefer one importing/aggregating root over several isolated submodules; ties are deterministic.
            var next = remaining.stream().min(Comparator.<String>comparingLong(name -> -remaining.stream()
                    .filter(other -> theories.get(name).covers(theories.get(other))).count())
                    .thenComparing(Comparator.naturalOrder())).orElseThrow();
            selected.add(next);
        }
        var coverage = new TreeMap<String, String>();
        for (var name : names) {
            String root = selected.contains(name) ? name : selected.stream()
                    .filter(candidate -> theories.get(candidate).covers(theories.get(name))).findFirst().orElseThrow();
            coverage.put(name, root);
        }
        return new Plan(List.copyOf(selected), coverage);
    }
}
