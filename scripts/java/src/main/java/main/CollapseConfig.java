package main;

import org.semanticweb.owlapi.model.OWLOntology;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@ConfigurationProperties
public record CollapseConfig(String ontology, Path out) {

	public boolean isReferenceOf(final OWLOntology ontology) {
		final var iri = ontology.getOntologyID().getOntologyIRI();
		return iri.isPresent() && iri.get().toString().endsWith(ontology + ".owl");
	}

}
