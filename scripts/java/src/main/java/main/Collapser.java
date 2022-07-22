package main;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.Priority;
import java.io.IOException;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Supplier;

@Component
@Priority(Integer.MAX_VALUE - 1)
public class Collapser implements CommandLineRunner {

	/**
	 * {@link Logger} of this class.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(Collapser.class);
	private final OntologyManager ontologyManager;
	private final OntologyConfig ontologyConfig;
	private final OWLOntologyManager collapseManager;
	private final String versionInfo;

	@Autowired
	public Collapser(final OntologyManager ontologyManager, final OntologyConfig ontologyConfig,
	                 @Value("${versionInfo}") final String versionInfo) {
		this.ontologyManager = ontologyManager;
		this.ontologyConfig = ontologyConfig;
		collapseManager = OWLManager.createOWLOntologyManager();
		this.versionInfo = versionInfo;
	}

	@Override
	public void run(final String... args)
			throws OWLOntologyStorageException, OWLOntologyCreationException, IOException {
		for (final var collapseConfig : ontologyConfig.toCollapse()) {
			collapse(collapseConfig);
		}
	}

	private void collapse(final CollapseConfig collapseConfig)
			throws OWLOntologyCreationException, OWLOntologyStorageException {
		final var toCollapse = getReferencedOntology(collapseConfig);
		final var requestedIRI = getRequestedIRI(collapseConfig, toCollapse);

		final var collapsed = collapseManager.createOntology(requestedIRI);

		LOGGER.info("Collapsing {}", collapseConfig.ontology());
		addAxioms(collapsed, toCollapse, collapseConfig.except());

		LOGGER.info("Adding versionInfo to {}", collapseConfig.ontology());
		final var df = OWLManager.getOWLDataFactory();
		final var versionAnnotation = df.getOWLAnnotation(df.getOWLVersionInfo(), df.getOWLLiteral(versionInfo));
		collapsed.getOWLOntologyManager().applyChange(new AddOntologyAnnotation(collapsed, versionAnnotation));

		LOGGER.info("Saving collapsed {} to {} ", collapseConfig.ontology(),
		            IRI.create(collapseConfig.outPath().toUri()));
		collapsed.saveOntology(IRI.create(collapseConfig.outPath().toUri()));
	}

	private static void addAxioms(final OWLOntology collapsed, final OWLOntology toCollapse,
	                              final Collection<String> except) {
		if (CollectionUtils.isEmpty(except)) {
			LOGGER.info("No exceptions that should be imported instead of being merged");
			// easy way, might also be faster
			collapsed.addAxioms(toCollapse.axioms(Imports.INCLUDED));
			return;
		}
		// hard way
		toCollapse.imports().forEach(next -> {
			if (CollapseConfig.containsReferenceOf(except, next)) {
				final var iri = next.getOntologyID().getOntologyIRI().get();
				LOGGER.info("{} will be imported instead of merged", iri);
				collapsed.applyChange(
						new AddImport(collapsed, OWLManager.getOWLDataFactory().getOWLImportsDeclaration(iri)));
			} else {
				collapsed.addAxioms(next.axioms(Imports.EXCLUDED));
			}
		});
	}

	private static IRI getRequestedIRI(final CollapseConfig collapseConfig, final HasOntologyID toCollapse) {
		return Optional.ofNullable(collapseConfig.newIri()).or(() -> toCollapse.getOntologyID().getOntologyIRI())
		               .orElseThrow(exceptionSupplier("No requested IRI: " + collapseConfig));
	}

	private OWLOntology getReferencedOntology(final CollapseConfig collapseConfig) {
		return ontologyManager.getOntologyManager().ontologies().filter(collapseConfig::isReferenceOf).findFirst()
		                      .orElseThrow(exceptionSupplier("Cannot resolve " + collapseConfig));
	}

	private static Supplier<RuntimeException> exceptionSupplier(final String message) {
		return () -> {
			throw new RuntimeException(message);
		};
	}


}
