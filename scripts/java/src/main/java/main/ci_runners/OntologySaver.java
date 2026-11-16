package main.ci_runners;

import main.OntologyManager;
import main.config.OntologyConfig;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Saves every loaded ontology under its original file name into the output directory. The sources stay untouched.
 */
public class OntologySaver implements CIRunnable {

	/**
	 * {@link Logger} of this class.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(OntologySaver.class);
	private final OntologyManager ontologyManager;
	private final OntologyConfig ontologyConfig;

	public OntologySaver(final OntologyManager ontologyManager, final OntologyConfig ontologyConfig) {
		this.ontologyManager = ontologyManager;
		this.ontologyConfig = ontologyConfig;
	}

	@Override
	public void run() throws IOException, OWLOntologyStorageException {
		final var manager = ontologyManager.getOntologyManager();
		Files.createDirectories(ontologyConfig.outputDirectory());
		for (final OWLOntology ontology : manager.getOntologies()) {
			final var source = Path.of(manager.getOntologyDocumentIRI(ontology).toURI());
			final var target = ontologyConfig.outputDirectory().resolve(source.getFileName());
			final var format = ontologyConfig.format() == null ? manager.getOntologyFormat(ontology)
					: ontologyConfig.format();
			LOGGER.info("Saving {} to {}", ontology.getOntologyID().getOntologyIRI().map(Object::toString)
			                                       .orElse("unnamed ontology"), target);
			ontology.saveOntology(format, IRI.create(target.toUri()));
		}
	}
}
