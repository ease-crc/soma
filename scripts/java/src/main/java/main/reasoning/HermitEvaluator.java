package main.reasoning;

import main.GuardingXmlCatalogIriMapper;
import org.semanticweb.HermiT.ReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.Imports;
import java.nio.file.*;
import java.util.List;

/** Local-only OWLAPI adapter. A fresh manager and reasoner are used for each entry point. */
public final class HermitEvaluator {
    private HermitEvaluator() {}

    public static ReasoningReport evaluate(Path input, Path catalogPath) {
        try {
            var manager = OWLManager.createOWLOntologyManager();
            GuardingXmlCatalogIriMapper catalog = catalogPath == null ? null
                    : new GuardingXmlCatalogIriMapper(catalogPath.toFile());
            manager.getIRIMappers().add(iri -> {
                if (catalog == null) throw new IllegalArgumentException("Missing local catalog mapping: " + iri);
                return catalog.getDocumentIRI(iri);
            });
            manager.setOntologyLoaderConfiguration(new OWLOntologyLoaderConfiguration()
                    .setMissingImportHandlingStrategy(MissingImportHandlingStrategy.THROW_EXCEPTION));
            var ontology = manager.loadOntologyFromOntologyDocument(input.toFile());
            // Validate cached imports too: an ontology IRI already in the manager must not bypass the catalog.
            for (var member : ontology.getImportsClosure()) {
                for (var declaration : member.getImportsDeclarations()) {
                    var expected = catalog == null ? null : catalog.entries().get(declaration.getIRI());
                    if (expected == null) throw new IllegalArgumentException("Missing local catalog mapping: " + declaration.getIRI());
                    var imported = manager.getImportedOntology(declaration);
                    if (imported == null || !Path.of(manager.getOntologyDocumentIRI(imported).toURI()).toRealPath().equals(expected))
                        throw new IllegalArgumentException("Import does not match catalog: " + declaration.getIRI());
                }
            }
            var reasoner = new ReasonerFactory().createReasoner(ontology);
            try {
                if (!reasoner.isConsistent()) return new ReasoningReport(true, false,
                        List.of(), List.of(), List.of(), List.of(), List.of());
                var df = manager.getOWLDataFactory();
                return new ReasoningReport(true, true, List.of(),
                        reasoner.getUnsatisfiableClasses().entities().map(c -> c.getIRI().toString()).toList(),
                        ontology.objectPropertiesInSignature(Imports.INCLUDED).filter(p -> !p.isOWLBottomObjectProperty())
                                .filter(p -> !reasoner.isSatisfiable(df.getOWLObjectSomeValuesFrom(p, df.getOWLThing())))
                                .map(p -> p.getIRI().toString()).toList(),
                        ontology.dataPropertiesInSignature(Imports.INCLUDED).filter(p -> !p.isOWLBottomDataProperty())
                                .filter(p -> !reasoner.isSatisfiable(df.getOWLDataSomeValuesFrom(p, df.getTopDatatype())))
                                .map(p -> p.getIRI().toString()).toList(), List.of());
            } finally { reasoner.dispose(); }
        } catch (Exception e) {
            return ReasoningReport.toolError("Load/reasoner failure: " + e);
        }
    }
}
