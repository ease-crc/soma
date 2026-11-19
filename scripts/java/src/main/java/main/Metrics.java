package main;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.semanticweb.owlapi.util.DLExpressivityChecker;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Computes the ontology metrics shown on the SOMA website and writes them as metrics.json and metrics.js. Names and
 * definitions follow OntoMetrics, which computed them before; "Total" counts include the imports. Imports are
 * resolved from files with the same name in the directory of the input.
 */
public final class Metrics {

	private Metrics() {
	}

	public static void main(final String... args) throws Exception {
		if (args.length != 2) {
			throw new IllegalArgumentException("Usage: Metrics <ontology file> <output directory>");
		}
		final var input = new File(args[0]).getAbsoluteFile();
		final var manager = OWLManager.createOWLOntologyManager();
		manager.getIRIMappers().add(iri -> {
			final var file = new File(input.getParentFile(), iri.getShortForm());
			if (!file.exists()) {
				throw new IllegalArgumentException("Cannot resolve import " + iri + " in " + input.getParent());
			}
			return IRI.create(file);
		});
		write(compute(manager.loadOntologyFromOntologyDocument(input)), Path.of(args[1]));
	}

	public static Map<String, Map<String, String>> compute(final OWLOntology ontology) {
		final var own = Imports.EXCLUDED;
		final var all = Imports.INCLUDED;
		final int classes = ontology.getClassesInSignature(all).size();
		final int objectProperties = ontology.getObjectPropertiesInSignature(all).size();

		final var base = new LinkedHashMap<String, String>();
		base.put("Axioms", count(ontology.getAxiomCount(own)));
		base.put("Logicalaxiomscount", count(ontology.getLogicalAxiomCount(own)));
		base.put("Classcount", count(ontology.getClassesInSignature(own).size()));
		base.put("Totalclassescount", count(classes));
		base.put("Objectpropertycount", count(ontology.getObjectPropertiesInSignature(own).size()));
		base.put("Totalobjectpropertiescount", count(objectProperties));
		base.put("Datapropertycount", count(ontology.getDataPropertiesInSignature(own).size()));
		base.put("Totaldatapropertiescount", count(ontology.getDataPropertiesInSignature(all).size()));
		base.put("Propertiescount", count(ontology.getObjectPropertiesInSignature(own).size()
				+ ontology.getDataPropertiesInSignature(own).size()));
		base.put("Individualcount", count(ontology.getIndividualsInSignature(own).size()));
		base.put("Totalindividualscount", count(ontology.getIndividualsInSignature(all).size()));
		base.put("DLexpressivity", expressivity(ontology));

		final var schema = new LinkedHashMap<String, String>();
		schema.put("Attributerichness", ratio(ontology.getDataPropertiesInSignature(all).size(), classes));
		schema.put("Inheritancerichness", ratio(ontology.getAxiomCount(AxiomType.SUBCLASS_OF, all), classes));
		schema.put("Equivalenceratio", ratio(ontology.getAxiomCount(AxiomType.EQUIVALENT_CLASSES, all), classes));
		schema.put("Axiomclassratio", ratio(ontology.getAxiomCount(own), classes));
		schema.put("Inverserelationsratio",
				ratio(ontology.getAxiomCount(AxiomType.INVERSE_OBJECT_PROPERTIES, all), objectProperties));

		final var metrics = new LinkedHashMap<String, Map<String, String>>();
		metrics.put("Basemetrics", base);
		metrics.put("Schemametrics", schema);
		return metrics;
	}

	private static String expressivity(final OWLOntology ontology) {
		final var languages = new DLExpressivityChecker(ontology.getImportsClosure()).expressibleInLanguages();
		var name = languages.isEmpty() ? "" : languages.iterator().next().toString();
		// The OWL API names e.g. SROIQ(D) as ALCROIQD: complex role inclusions (R) include transitive roles (S),
		// and a trailing D stands for datatypes, usually written (D).
		if (name.startsWith("ALCR")) {
			name = "SR" + name.substring("ALCR".length());
		}
		return name.endsWith("D") ? name.substring(0, name.length() - 1) + "(D)" : name;
	}

	private static String count(final int value) {
		return Integer.toString(value);
	}

	/** Six decimals without trailing zeros, as OntoMetrics printed them (e.g. "1.54878", "0.0"). */
	private static String ratio(final int numerator, final int denominator) {
		if (denominator == 0) {
			return "0.0";
		}
		final var value = BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), 6, RoundingMode.HALF_UP)
		                            .stripTrailingZeros().toPlainString();
		return value.contains(".") ? value : value + ".0";
	}

	private static void write(final Map<String, Map<String, String>> metrics, final Path directory) throws IOException {
		final var groups = metrics.entrySet().stream().map(group -> "\"" + group.getKey() + "\": {"
				+ group.getValue().entrySet().stream().map(e -> "\"" + e.getKey() + "\": \"" + e.getValue() + "\"")
				       .collect(Collectors.joining(", ")) + "}").collect(Collectors.joining(", "));
		final var json = "{\"OntologyMetrics\": {\"BaseMetrics\": {" + groups + "}}}";
		Files.createDirectories(directory);
		Files.writeString(directory.resolve("metrics.json"), json + "\n");
		// The website includes this file with a script tag.
		Files.writeString(directory.resolve("metrics.js"), "metric_data='" + json + "';\n");
	}

}
