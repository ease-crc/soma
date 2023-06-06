package main.ci_runners;

import main.OntologyManager;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Lazy
public class LabelCommentChecker implements CIRunnable {
    /**
     * {@link Logger} of this class.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(LabelCommentChecker.class);

    private final OntologyManager ontologyManager;

    @Autowired
    public LabelCommentChecker(final OntologyManager ontologyManager) {
        this.ontologyManager = ontologyManager;
    }

    @Override
    public void run() {
        StringBuilder errorString = new StringBuilder();
        for (final OWLOntology ontology : ontologyManager.getOntologyManager().getOntologies()) {
            errorString.append(checkLabelsComments(ontology));
        }
        if (errorString.length() > 0) {
            throw new RuntimeException("Found missing labels or comments:\n" + errorString);
        }
    }

    private String checkLabelsComments(final OWLOntology ontology) {
        LOGGER.info("Checking labels and comments in {}", ontology.getOntologyID().getOntologyIRI());
        StringBuilder errorString = new StringBuilder();

        for (final var entity : ontology.getSignature()) {
            if (entity.isBuiltIn() || entity instanceof OWLAnnotationProperty) {
                continue;
            }
            final var properties = ontology.getAnnotationAssertionAxioms(entity.getIRI(), Imports.INCLUDED);
            boolean hasLabel = properties.stream().anyMatch(axiom -> axiom.getProperty().isLabel());
            boolean hasComment = properties.stream().anyMatch(axiom -> axiom.getProperty().isComment());

            String missing = "";
            if (!hasLabel && !hasComment)   missing = "label and comment";
            else if (!hasLabel)             missing = "label";
            else if (!hasComment)           missing = "comment";

            if (!missing.isEmpty()) {
                LOGGER.error("Entity {} is missing {}", entity, missing);
                errorString.append(missing).append(": ").append(entity.getIRI()).append("\n");
            }
        }
        return errorString.toString();
    }
}

