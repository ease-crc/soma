package main;

import main.config.OntologyConfig;
import org.jgrapht.Graph;
import org.jgrapht.alg.connectivity.GabowStrongConnectivityInspector;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
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
import java.util.stream.Stream;

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
		final OWLOntologyIRIMapper xmlCatalogIriMapper = new XMLCatalogIRIMapper(
				ontologyDirectory.resolve(XML_CATALOG_PATH).toFile());
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

	/**
	 * @return A {@link Stream} of a subset of the managed {@link OWLOntology}s such that their imports include all
	 * managed ontologies
	 */
	public Stream<OWLOntology> mainOntologies() {
		// condensate import structure to strongly connected components
		final var condensation = new GabowStrongConnectivityInspector<>(graphOfImportStructure()).getCondensation();

		// get roots of condensation
		//noinspection OptionalGetWithoutIsPresent
		return condensation.vertexSet().stream().filter(key -> condensation.incomingEdgesOf(key).isEmpty())
		                   .map(next -> next.vertexSet().stream().findAny().get());
	}

	/**
	 * @return A {@link Graph} with all managed {@link OWLOntology}s as vertices and edges from o1 to o2 if o1 imports
	 * o2
	 */
	public Graph<OWLOntology, DefaultEdge> graphOfImportStructure() {
		final DefaultDirectedGraph<OWLOntology, DefaultEdge> graph = new DefaultDirectedGraph<>(DefaultEdge.class);
		ontologyManager.ontologies().forEach(graph::addVertex);
		ontologyManager.ontologies().forEach(next -> next.getDirectImports().forEach(imp -> graph.addEdge(next, imp)));
		return graph;
	}
}
