package main.ci_runners;

import main.OntologyManager;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.stream.Collectors;

@Component
@Lazy
public class PropertyListExporter implements CIRunnable {

	private static final Logger logger = LoggerFactory.getLogger(PropertyListExporter.class);

	private final Path path;
	private final OntologyManager ontologyManager;

	@Autowired
	public PropertyListExporter(final OntologyManager ontologyManager,
	                            @Value("${propertyListPath:#{null}}") final Path path) {
		this.path = path;
		this.ontologyManager = ontologyManager;
	}

	@Override
	public void run() throws IOException {
		if (path == null) {
			logger.info("CL argument 'propertyListPath' not given. Skipping export of properties.");
			return;
		}
		for (final OWLOntology owlOntology : ontologyManager.mainOntologies().collect(Collectors.toSet())) {
			exportProperties(owlOntology);
		}
	}

	private void exportProperties(final OWLOntology ontology) throws IOException {
		@SuppressWarnings("OptionalGetWithoutIsPresent") String fileName = ontology.getOntologyID().getOntologyIRI()
		                                                                           .get().getShortForm();
		fileName = fileName.substring(0, fileName.length() - 5);
		if (!path.toFile().exists() && !path.toFile().mkdirs()) {
			throw new IOException("Could not create dir " + path.toAbsolutePath());
		}
		logger.info("Writing object- and dataProperties of {} to {}_[object,data]Properties", fileName,
		            path.resolve(fileName).toAbsolutePath());
		Files.write(path.resolve(fileName + "_objectProperties"),
		            ontology.objectPropertiesInSignature(Imports.INCLUDED)
		                                                                  .map(next -> (CharSequence) next.toString())::iterator,
		            StandardOpenOption.CREATE);
		Files.write(path.resolve(fileName + "_dataProperties"), ontology.dataPropertiesInSignature(Imports.INCLUDED)
		                                                                .map(next -> (CharSequence) next.toString())::iterator,
		            StandardOpenOption.CREATE);
	}
}
