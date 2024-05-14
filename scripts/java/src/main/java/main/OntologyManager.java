package main;

import main.config.OntologyConfig;
import org.protege.xmlcatalog.owlapi.XMLCatalogIRIMapper;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Class to manage the resources that are associated with an {@link OWLOntology}.
 */
@Component
@Lazy
public class OntologyManager {

	private static final Path XML_CATALOG_PATH = Path.of("catalog-v001.xml");

	/**
	 * {@link Logger} of this class.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(OntologyManager.class);

	/**
	 * {@link OWLOntologyManager} that holds the managed ontology.
	 */
	private final OWLOntologyManager ontologyManager = OWLManager.createConcurrentOWLOntologyManager();

	@Autowired
	public OntologyManager(final OntologyConfig config) throws OWLOntologyCreationException, IOException {
		loadCatalog(config.directory());
		loadOntologies(config.directory());
	}

	@SuppressWarnings("OverlyBroadThrowsClause")
	private void loadCatalog(final Path ontologyDirectory) throws IOException {
		// use this if you want to prohibit ontologies being loaded from online
		final OWLOntologyIRIMapper xmlCatalogIriMapper = new GuardingXmlCatalogIriMapper(
				ontologyDirectory.resolve(XML_CATALOG_PATH).toFile());
		// use this if you want to allow to load ontologies from online
		// final OWLOntologyIRIMapper xmlCatalogIriMapper = new XMLCatalogIRIMapper(
		// 		ontologyDirectory.resolve(XML_CATALOG_PATH).toFile());
		ontologyManager.getIRIMappers().add(xmlCatalogIriMapper);
	}

	private void loadOntologies(final Path ontologyDirectory) throws OWLOntologyCreationException, IOException {
		// traverse all .owl files and load the ontologies
		try (final DirectoryStream<Path> stream = Files.newDirectoryStream(ontologyDirectory, "*.{owl}")) {
			for (final Path entry : stream) {
				loadOntology(entry.toFile());
			}
		} catch (final DirectoryIteratorException ex) {
			// I/O error encountered during the iteration, the cause is an IOException
			throw ex.getCause();
		}
	}

	private void loadOntology(final File ontologyFile) throws OWLOntologyCreationException {
		try {
			LOGGER.info("Loading '{}'", ontologyFile.getName());
			ontologyManager.loadOntologyFromOntologyDocument(ontologyFile);
			LOGGER.info("Loaded '{}'", ontologyFile.getName());
		} catch (final OWLOntologyAlreadyExistsException e) {
			LOGGER.info("Skipping '{}' (already loaded)", ontologyFile.getName());
			// was already imported via any other file; do nothing
		}
	}

	public OWLOntologyManager getOntologyManager() {
		return ontologyManager;
	}
}
