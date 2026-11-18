package main;

import org.semanticweb.HermiT.ReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLObjectPropertyExpression;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.profiles.OWL2DLProfile;
import org.semanticweb.owlapi.profiles.OWLProfileViolation;
import org.semanticweb.owlapi.reasoner.InferenceType;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks ontologies with HermiT: OWL 2 DL profile, consistency, unsatisfiable classes and object properties.
 * Imports are resolved only through the catalog-v001.xml next to each file. Exits with 1 if anything was found.
 */
public final class HermitCheck {

	/**
	 * Result of a check. {@code consistent} is null if the ontology is outside OWL 2 DL and was not reasoned over.
	 */
	public record Result(List<String> profileViolations, Boolean consistent, List<String> unsatisfiableClasses,
	                     List<String> unsatisfiableObjectProperties) {

		public boolean passed() {
			return profileViolations.isEmpty() && Boolean.TRUE.equals(consistent) && unsatisfiableClasses.isEmpty()
					&& unsatisfiableObjectProperties.isEmpty();
		}
	}

	private HermitCheck() {
	}

	public static void main(final String... files) throws Exception {
		if (files.length == 0) {
			throw new IllegalArgumentException("Usage: HermitCheck <ontology file>...");
		}
		var passed = true;
		for (final var file : files) {
			final var result = check(new File(file));
			print(file, result);
			passed &= result.passed();
		}
		System.exit(passed ? 0 : 1);
	}

	public static Result check(final File file) throws Exception {
		final var manager = OWLManager.createOWLOntologyManager();
		final var catalog = new File(file.getAbsoluteFile().getParentFile(), "catalog-v001.xml");
		if (catalog.exists()) {
			manager.getIRIMappers().add(new GuardingXmlCatalogIriMapper(catalog));
		} else {
			manager.getIRIMappers().add(iri -> {
				throw new IllegalArgumentException("No catalog-v001.xml next to " + file + " to resolve " + iri);
			});
		}
		final OWLOntology ontology = manager.loadOntologyFromOntologyDocument(file);

		final var violations = new OWL2DLProfile().checkOntology(ontology).getViolations().stream()
		                                          .map(OWLProfileViolation::toString).sorted().toList();
		// HermiT rejects ontologies outside OWL 2 DL with an exception, so only reason over valid ones.
		if (!violations.isEmpty()) {
			return new Result(violations, null, List.of(), List.of());
		}

		final var reasoner = new ReasonerFactory().createReasoner(ontology);
		try {
			if (!reasoner.isConsistent()) {
				return new Result(List.of(), false, List.of(), List.of());
			}
			reasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY, InferenceType.OBJECT_PROPERTY_HIERARCHY);
			final var classes = reasoner.getUnsatisfiableClasses().getEntitiesMinusBottom().stream()
			                            .map(c -> c.getIRI().toString()).sorted().toList();
			// Inverses of empty properties are empty as well; report only the named properties.
			final var properties = reasoner.getBottomObjectPropertyNode().getEntitiesMinusBottom().stream()
			                               .filter(OWLObjectPropertyExpression::isNamed)
			                               .map(p -> p.getNamedProperty().getIRI().toString()).sorted().toList();
			return new Result(List.of(), true, classes, properties);
		} finally {
			reasoner.dispose();
		}
	}

	private static void print(final String file, final Result result) {
		final var lines = new ArrayList<String>();
		result.profileViolations().forEach(v -> lines.add("Not in OWL 2 DL: " + v));
		if (Boolean.FALSE.equals(result.consistent())) {
			lines.add("Ontology is inconsistent");
		}
		result.unsatisfiableClasses().forEach(c -> lines.add("Unsatisfiable class: " + c));
		result.unsatisfiableObjectProperties().forEach(p -> lines.add("Unsatisfiable object property: " + p));
		if (lines.isEmpty()) {
			System.out.println(file + ": OK");
			return;
		}
		for (final var line : lines) {
			// Rendered as an annotation by GitHub Actions, plain text elsewhere.
			System.out.println("::error file=" + escape(file).replace(",", "%2C").replace(":", "%3A") + "::"
					+ escape(line));
		}
	}

	/** Escaping required by GitHub workflow commands. */
	private static String escape(final String value) {
		return value.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A");
	}

}
