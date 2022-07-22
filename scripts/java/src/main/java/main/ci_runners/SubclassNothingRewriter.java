package main.ci_runners;

import main.OntologyManager;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Lazy
public class SubclassNothingRewriter implements CIRunnable {

	/**
	 * {@link Logger} of this class.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(SubclassNothingRewriter.class);

	private final OntologyManager ontologyManager;

	@Autowired
	public SubclassNothingRewriter(final OntologyManager ontologyManager) {
		this.ontologyManager = ontologyManager;
	}


	@Override
	public void run() {
		ontologyManager.getOntologyManager().getOntologies().forEach(this::rewriteGCIs);
	}

	private void rewriteGCIs(final OWLOntology ontology) {
		final var iri = ontology.getOntologyID().getOntologyIRI().get();
		final var gcis = ontology.getGeneralClassAxioms();
		if (gcis.isEmpty()) {
			return;
		}
		LOGGER.info("Rewriting the following GCIs from ontology {}", iri);

		gcis.forEach(next -> {
			final Optional<OWLAxiom> rewritten = rewrite(next);
			if (rewritten.isEmpty()) {
				return;
			}
			LOGGER.info("Rewrote {} to {}", next, rewritten.get());
			ontology.removeAxiom(next);
			ontology.add(rewritten.get());
		});

	}

	private static Optional<OWLAxiom> rewrite(final OWLAxiom gci) {
		final var df = OWLManager.getOWLDataFactory();
		if (gci instanceof OWLSubClassOfAxiom subClassOfAxiom) {
			if (!subClassOfAxiom.getSuperClass().isOWLNothing()) {
				return Optional.empty();
			}
			if (subClassOfAxiom.getSubClass() instanceof OWLObjectIntersectionOf intersection) {
				return Optional.of(df.getOWLDisjointClassesAxiom(intersection.operands(), gci.getAnnotations()));
			}
			if (subClassOfAxiom.getSubClass() instanceof OWLObjectSomeValuesFrom someValuesFrom) {
				return Optional.of(df.getOWLObjectPropertyRangeAxiom(someValuesFrom.getProperty(),
				                                                     df.getOWLObjectComplementOf(
						                                                     someValuesFrom.getFiller()),
				                                                     gci.getAnnotations()));
			}
		}
		return Optional.empty();
	}
}
