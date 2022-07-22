package main;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.AddOntologyAnnotation;
import org.semanticweb.owlapi.model.OWLOntology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class VersionInfoAdder implements CIRunnable {


	/**
	 * {@link Logger} of this class.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(VersionInfoAdder.class);

	private final OntologyManager ontologyManager;


	private final String versionInfo;

	@Autowired
	public VersionInfoAdder(final OntologyManager ontologyManager, @Value("${versionInfo}") final String versionInfo) {
		this.ontologyManager = ontologyManager;
		this.versionInfo = versionInfo;
	}


	@Override
	public void run() {
		for (final OWLOntology ontology : ontologyManager.getOntologyManager().getOntologies()) {
			addVersionInfo(ontology, versionInfo);
		}
	}

	private static void addVersionInfo(final OWLOntology ontology, final String version) {

		final var df = OWLManager.getOWLDataFactory();
		final var versionAnnotation = df.getOWLAnnotation(df.getOWLVersionInfo(), df.getOWLLiteral(version));
		ontology.getOWLOntologyManager().applyChange(new AddOntologyAnnotation(ontology, versionAnnotation));

		LOGGER.info("Added versionInfo {} to {}", version, ontology.getOntologyID().getOntologyIRI());
	}


}
