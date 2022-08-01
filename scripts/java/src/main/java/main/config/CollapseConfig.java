package main.config;

import org.semanticweb.owlapi.model.HasOntologyID;
import org.semanticweb.owlapi.model.IRI;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@ConfigurationProperties
public record CollapseConfig(String ontology, Path outPath, List<String> except, IRI newIri) {

	public boolean isReferenceOf(final HasOntologyID ontology) {
		return containsReferenceOf(Collections.singleton(this.ontology), ontology);
	}

	public static boolean containsReferenceOf(final Collection<String> references, final HasOntologyID ontology) {
		final var iri = ontology.getOntologyID().getOntologyIRI();
		return iri.isPresent() && references.stream().anyMatch(next -> iri.get().toString().endsWith(next + ".owl"));
	}

}
