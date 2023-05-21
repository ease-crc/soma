package main.ci_runners;

import main.OntologyManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.ChangeApplied;
import org.semanticweb.owlapi.util.OWLEntityRenamer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Lazy
public class IRINamespaceRewriter implements CIRunnable {
    /**
     * {@link Logger} of this class.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(IRINamespaceRewriter.class);

    private final OntologyManager ontologyManager;

    @Autowired
    public IRINamespaceRewriter(final OntologyManager ontologyManager) {
        this.ontologyManager = ontologyManager;
    }

    @Override
    public void run() {
        for (final OWLOntology ontology : ontologyManager.getOntologyManager().getOntologies()) {
            rewriteOntologyIRI(ontology);
        }
    }

    private void rewriteOntologyIRI(final OWLOntology ontology) {
        if (ontology.getOntologyID().getOntologyIRI().isEmpty()) {
            LOGGER.warn("Skipping ontology without IRI");
            return;
        }
        final IRI ontologyIRI = ontology.getOntologyID().getOntologyIRI().get();
        final var manager = ontologyManager.getOntologyManager();
        final var renamer = new OWLEntityRenamer(manager, ontology.getImportsClosure());

        LOGGER.info("Checking ontology {} for SOMA namespace", ontologyIRI);
        if (ontologyIRI.toString().contains("SOMA")) {
            for (final var entity : ontology.getSignature()) {
                rewriteEntityIRI(entity, manager, renamer);
            }
        }
    }

    private void rewriteEntityIRI(final OWLEntity entity, final OWLOntologyManager manager, final OWLEntityRenamer renamer) {
        final IRI entityIRI = entity.getIRI();
        //LOGGER.info("Checking {} for SOMA namespace", entityIRI);

        if (entityIRI.toString().contains("SOMA-")) {
            LOGGER.info("Rewriting IRI of entity {}", entityIRI);
            final IRI newIRI = IRI.create(entityIRI.toString().replaceAll("SOMA-[^/]*\\.owl", "SOMA.owl"));

            final var changes = renamer.changeIRI(entity, newIRI);
            for (final var change : changes) {
                //LOGGER.info("Applying change {}", change);
                if (manager.applyChange(change) == ChangeApplied.UNSUCCESSFULLY) {
                    LOGGER.error("Could not apply change {}", change);
                }
            }
        }
    }
}
