package main.ci_runners;

import main.OntologyManager;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.OWLDeclarationAxiom;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.parameters.ChangeApplied;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;

@Component
@Lazy
public class IsDefinedInAdder implements CIRunnable {

	/**
	 * {@link Logger} of this class.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(IsDefinedInAdder.class);

	private final OntologyManager ontologyManager;

	@Autowired
	public IsDefinedInAdder(final OntologyManager ontologyManager) {
		this.ontologyManager = ontologyManager;
	}


	@Override
	public void run() {
		for (final OWLOntology ontology : ontologyManager.getOntologyManager().getOntologies()) {
			addIsDefinedIn(ontology);
		}
	}

	private static void addIsDefinedIn(final OWLOntology ontology) {
		if (ontology.getOntologyID().getOntologyIRI().isEmpty()) {
			LOGGER.warn("Skipping ontology without IRI");
			return;
		}
		final var ontologyIRI = ontology.getOntologyID().getOntologyIRI().get();
		LOGGER.info("Checking {} for missing rdfs:isDefinedIn", ontologyIRI);

		final var df = OWLManager.getOWLDataFactory();
		final var annotation = df.getOWLAnnotation(df.getRDFSIsDefinedBy(), ontologyIRI);

		// add to all declared entities
		final Collection<OWLEntity> addedEntities = new HashSet<>();
		final Iterable<OWLDeclarationAxiom> declarationAxioms = ontology.axioms(AxiomType.DECLARATION)::iterator;
		for (final OWLDeclarationAxiom axiom : declarationAxioms) {
			final var entity = axiom.getEntity();
			final var annotationAxiom = df.getOWLAnnotationAssertionAxiom(entity.getIRI(), annotation);
			final var changeApplied = ontology.add(annotationAxiom);
			if (changeApplied == ChangeApplied.SUCCESSFULLY) {
				addedEntities.add(entity);
			}
		}
		if (!addedEntities.isEmpty()) {
			LOGGER.info("Added annotation {} to entities: {}", annotation, addedEntities);
		}
	}


}
