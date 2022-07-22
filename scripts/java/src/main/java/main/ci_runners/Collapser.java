package main.ci_runners;

import main.OntologyManager;
import main.config.CollapseConfig;
import main.config.OntologyConfig;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Component
public class Collapser implements CIRunnable {

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
	public void run() throws OWLOntologyCreationException, IOException, OWLOntologyStorageException {
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
		final Set<OWLOntology> importingImports = toCollapse.imports()
		                                                    .filter(next -> CollapseConfig.containsReferenceOf(except,
		                                                                                                       next))
		                                                    .collect(Collectors.toSet());
		importingImports.forEach(next -> System.out.println(next.getOntologyID().getOntologyIRI()));

		final Set<OWLOntology> transitiveImports = importingImports.stream().flatMap(OWLOntology::imports)
		                                                           .collect(Collectors.toSet());
		final Set<OWLOntology> mergingImports = new HashSet<>();
		toCollapse.imports().filter(next -> !(importingImports.contains(next) || transitiveImports.contains(next)))
		          .collect(Collectors.toSet()).forEach(mergingImports::add);
		mergingImports.add(toCollapse);

		importingImports.forEach(next -> {
			final var iri = next.getOntologyID().getOntologyIRI().get();
			LOGGER.info("{} will be imported instead of merged", iri);
			collapsed.applyChange(
					new AddImport(collapsed, OWLManager.getOWLDataFactory().getOWLImportsDeclaration(iri)));
		});

		mergingImports.forEach(next -> collapsed.addAxioms(next.axioms(Imports.EXCLUDED)));

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
