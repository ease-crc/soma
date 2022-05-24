package rest.ontology;

import com.google.common.io.FileBackedOutputStream;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.expression.OWLEntityChecker;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyLoaderConfiguration;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.util.AnnotationValueShortFormProvider;
import org.semanticweb.owlapi.util.BidirectionalShortFormProviderAdapter;
import org.semanticweb.owlapi.util.SimpleShortFormProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import rest.converter.ManchesterLabelEntityChecker;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Class to manage the resources that are associated with an {@link OWLOntology}.
 */
@Component
@Lazy
public class OntologyManager {

	/**
	 * {@link Logger} of this class.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(OntologyManager.class);

	/**
	 * {@link OWLOntologyManager} that holds the managed ontology.
	 */
	private final OWLOntologyManager ontologyManager = OWLManager.createConcurrentOWLOntologyManager();

	public OntologyManager(@Value("${ontology.directory}") final Path ontologyDirectory)
			throws OWLOntologyCreationException, FileNotFoundException {
		loadOntology(ontologyDirectory);
	}

	private OWLOntology loadOntology(final Path ontologyDirectory) throws OWLOntologyCreationException, IOException {
		OWLOntologyLoaderConfiguration configuration = new OWLOntologyLoaderConfiguration();
		configuration.
		Files.list(ontologyDirectory).forEach(next -> ontologyManager.l);

		final var loaded = ontologyManager.loadOntologyFromOntologyDocument(ontologyFile);
		LOGGER.info("Loaded '{}'", loaded.getOntologyID());
		return loaded;
	}


}
