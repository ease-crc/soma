package main;

import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.annotation.Priority;

@Component
@Priority(Integer.MAX_VALUE - 1)
public class OntologySaver implements CommandLineRunner {

	/**
	 * {@link Logger} of this class.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(OntologySaver.class);
	private final OntologyManager ontologyManager;

	@Autowired
	public OntologySaver(final OntologyManager ontologyManager) {
		this.ontologyManager = ontologyManager;
	}

	@Override
	public void run(final String... args) throws OWLOntologyStorageException {
		for (final OWLOntology ontology : ontologyManager.getOntologyManager().getOntologies()) {
			LOGGER.info("Saving {}", ontology.getOntologyID().getOntologyIRI());
			ontology.saveOntology();
		}
	}
}
