package main.reasoning;

import main.GuardingXmlCatalogIriMapper;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import java.nio.file.*;

/** Strict offline loading shared by both reasoners. */
public final class LocalReasoningOntology {
    private LocalReasoningOntology() {}
    public static OWLOntology load(Path input, Path catalogPath) throws Exception {
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
        return ontology;
    }
}
