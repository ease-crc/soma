package main;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.AddOntologyAnnotation;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.annotation.Priority;
import java.io.IOException;

@Component
@Priority(Integer.MAX_VALUE - 1)
public class Collapser implements CommandLineRunner {

    /**
     * {@link Logger} of this class.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(Collapser.class);
    private final OntologyManager ontologyManager;
    private final OntologyConfig ontologyConfig;

    @Value("${versionInfo}")
    private String versionInfo;

    @Autowired
    public Collapser(final OntologyManager ontologyManager, final OntologyConfig ontologyConfig) {
        this.ontologyManager = ontologyManager;
        this.ontologyConfig = ontologyConfig;
    }

    @Override
    public void run(final String... args)
            throws OWLOntologyStorageException, OWLOntologyCreationException, IOException {
        final var collapseManager = OWLManager.createOWLOntologyManager();
        for (final CollapseConfig collapseConfig : ontologyConfig.toCollapse()) {
            final var toCollapse = ontologyManager.getOntologyManager().ontologies()
                    .filter(collapseConfig::isReferenceOf).findFirst();

            if (toCollapse.isEmpty()) {
                throw new RuntimeException("Cannot resolve " + collapseConfig);
            }

            @SuppressWarnings("OptionalGetWithoutIsPresent") final var collapsed = collapseManager.createOntology(
                    toCollapse.get().getOntologyID().getOntologyIRI().get());

            LOGGER.info("Collapsing {}", collapseConfig.ontology());
            collapsed.addAxioms(toCollapse.get().axioms(Imports.INCLUDED));

            LOGGER.info("Adding versionInfo to {}", collapseConfig.ontology());
            final var df = OWLManager.getOWLDataFactory();
            final var versionAnnotation = df.getOWLAnnotation(df.getOWLVersionInfo(), df.getOWLLiteral(versionInfo));
            collapsed.getOWLOntologyManager().applyChange(new AddOntologyAnnotation(collapsed, versionAnnotation));

            LOGGER.info("Saving collapsed {} to {} ", collapseConfig.ontology(),
                    collapseConfig.outPath().toFile().getCanonicalFile());
            collapsed.saveOntology(IRI.create(collapseConfig.outPath().toUri()));
        }
    }


}
