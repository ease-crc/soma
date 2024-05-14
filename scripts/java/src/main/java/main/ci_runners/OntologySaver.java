package main.ci_runners;

import main.OntologyManager;
import main.config.OntologyConfig;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Lazy
public class OntologySaver implements CIRunnable {

	/**
	 * {@link Logger} of this class.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(OntologySaver.class);
	private final OntologyManager ontologyManager;
	private final OntologyConfig ontologyConfig;

	@Autowired
	public OntologySaver(final OntologyManager ontologyManager, final OntologyConfig ontologyConfig) {
		this.ontologyManager = ontologyManager;
		this.ontologyConfig = ontologyConfig;
	}

	@Override
	public void run() throws OWLOntologyStorageException {
		for (final OWLOntology ontology : ontologyManager.getOntologyManager().getOntologies()) {
			LOGGER.info("Saving {}", ontology.getOntologyID().getOntologyIRI().map(Object::toString)
			                                 .orElseGet(() -> "unnamed ontology"));
			if (ontologyConfig.format() == null) {
				ontology.saveOntology();
			} else {
				LOGGER.info("Using format {}", ontologyConfig.format());
				ontology.saveOntology(ontologyConfig.format());
			}
		}
	}
}
