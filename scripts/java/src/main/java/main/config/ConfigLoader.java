package main.config;

import main.converter.OWLDocumentFormatConverter;
import org.semanticweb.owlapi.model.IRI;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Reads application.yaml. Relative paths are resolved against the working directory.
 */
public final class ConfigLoader {

	private ConfigLoader() {
	}

	public static OntologyConfig load(final Path file) throws IOException {
		final Object root;
		try (final var input = Files.newInputStream(file)) {
			root = new Yaml(new SafeConstructor(new LoaderOptions())).load(input);
		}
		final var ontology = map(map(root, file.toString()).get("ontology"), "ontology");
		final var toCollapse = new ArrayList<CollapseConfig>();
		for (final Object entry : list(ontology.get("to-collapse"))) {
			final var collapse = map(entry, "ontology.to-collapse");
			final var newIri = collapse.get("new-iri");
			toCollapse.add(new CollapseConfig(
					required(collapse, "ontology"),
					Path.of(required(collapse, "out-path")),
					except(collapse.get("except")),
					newIri == null ? null : IRI.create(newIri.toString())));
		}
		final var format = ontology.get("format");
		return new OntologyConfig(
				Path.of(required(ontology, "directory")),
				toCollapse,
				format == null ? null : new OWLDocumentFormatConverter().convert(format.toString()));
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> map(final Object value, final String name) {
		if (!(value instanceof Map)) {
			throw new IllegalArgumentException("Expected a mapping for '" + name + "'");
		}
		return (Map<String, Object>) value;
	}

	private static List<?> list(final Object value) {
		return value == null ? List.of() : (List<?>) value;
	}

	private static String required(final Map<String, Object> map, final String key) {
		final var value = map.get(key);
		if (value == null) {
			throw new IllegalArgumentException("Missing '" + key + "'");
		}
		return value.toString();
	}

	/** Accepts a YAML list or a comma-separated string. */
	private static List<String> except(final Object value) {
		if (value == null) {
			return List.of();
		}
		final List<?> items = value instanceof List<?> l ? l : Arrays.asList(value.toString().split(","));
		return items.stream().map(Object::toString).map(String::trim).toList();
	}

}
