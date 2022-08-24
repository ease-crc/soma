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
	private static final OWLDataFactory df = OWLManager.getOWLDataFactory();
	private final OntologyManager ontologyManager;

	@Autowired
	public SubclassNothingRewriter(final OntologyManager ontologyManager) {
		this.ontologyManager = ontologyManager;
	}


	@Override
	public void run() {
		ontologyManager.getOntologyManager().getOntologies().forEach(SubclassNothingRewriter::rewriteGCIs);
	}

	private static void rewriteGCIs(final OWLOntology ontology) {
		@SuppressWarnings("OptionalGetWithoutIsPresent") final var iri = ontology.getOntologyID().getOntologyIRI()
		                                                                         .get();
		final var gcis = ontology.getGeneralClassAxioms();
		if (gcis.isEmpty()) {
			return;
		}
		LOGGER.info("Attempting to rewrite the following GCIs from ontology {}", iri);

		gcis.forEach(next -> {
			final Optional<OWLAxiom> rewritten = rewrite(next);
			if (rewritten.isEmpty()) {
				LOGGER.info("Unable to rewrite {}", next);
				return;
			}
			LOGGER.info("Rewrote {} to {}", next, rewritten.get());
			ontology.removeAxiom(next);
			ontology.add(rewritten.get());
		});

	}

	@SuppressWarnings("ChainOfInstanceofChecks")
	private static Optional<OWLAxiom> rewrite(final OWLAxiom gci) {
		if (gci instanceof OWLSubClassOfAxiom subClassOfAxiom) {
			if (!subClassOfAxiom.getSuperClass().isOWLNothing()) {
				return Optional.empty();
			}
			if (subClassOfAxiom.getSubClass() instanceof OWLObjectIntersectionOf intersection) {
				return rewriteIntersectionSubclassNothing(intersection);
			}
			if (subClassOfAxiom.getSubClass() instanceof OWLObjectSomeValuesFrom someValuesFrom) {
				final var rewriting = df.getOWLObjectPropertyRangeAxiom(someValuesFrom.getProperty(),
				                                                        df.getOWLObjectComplementOf(
						                                                        someValuesFrom.getFiller()),
				                                                        gci.getAnnotations());
				return Optional.of(rewriting);
			}
		}
		return Optional.empty();
	}

	private static Optional<OWLAxiom> rewriteIntersectionSubclassNothing(final OWLObjectIntersectionOf intersection) {
		var atomicClass = intersection.operands().filter(AsOWLClass::isOWLClass).findAny();
		if (atomicClass.isEmpty()) {
			atomicClass = intersection.operands().findFirst();
		}
		if (atomicClass.isEmpty()) {
			throw new IllegalArgumentException("Corrupted GCI:" + intersection + " subclass of owl:Nothing");
		}

		final OWLClassExpression finalAtomicClass = atomicClass.get();
		final var rewriting = df.getOWLSubClassOfAxiom(finalAtomicClass, df.getOWLObjectComplementOf(
				df.getOWLObjectIntersectionOf(intersection.operands().filter(next -> !next.equals(finalAtomicClass)))));
		return Optional.of(rewriting);
	}
}
