package main.ci_runners;

import main.OntologyManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Lazy
public class IRINamespaceChecker implements CIRunnable {
    /**
     * {@link Logger} of this class.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(IRINamespaceChecker.class);

    private final OntologyManager ontologyManager;

    @Autowired
    public IRINamespaceChecker(final OntologyManager ontologyManager) {
        this.ontologyManager = ontologyManager;
    }

    @Override
    public void run() {
        StringBuilder errorString = new StringBuilder();
        for (final OWLOntology ontology : ontologyManager.getOntologyManager().getOntologies()) {
            for (String iri : checkOntologyIRI(ontology)) {
                errorString.append(iri).append("\n");
            }
        }
        if (errorString.length() > 0) {
            throw new RuntimeException("Found IRIs with inconsistent SOMA namespace:\n" + errorString);
        }
    }

    private List<String> checkOntologyIRI(final OWLOntology ontology) {
        List<String> wrongIRIs = new ArrayList<>();

        if (ontology.getOntologyID().getOntologyIRI().isEmpty()) {
            LOGGER.warn("Skipping ontology without IRI");
            return wrongIRIs;
        }
        final IRI ontologyIRI = ontology.getOntologyID().getOntologyIRI().get();

        LOGGER.info("Checking ontology {} for SOMA namespace", ontologyIRI);
        if (ontologyIRI.toString().contains("SOMA")) {
            for (final var entity : ontology.getSignature()) {
                String entityIRI = entity.getIRI().toString();
                if (entityIRI.contains("SOMA-")) {
                    LOGGER.error("Found wrong IRI: {}", entityIRI);
                    wrongIRIs.add(entityIRI);
                }
            }
        }

        return wrongIRIs;
    }
}
