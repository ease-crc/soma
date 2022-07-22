package main;

import org.semanticweb.owlapi.model.HasOntologyID;
import org.semanticweb.owlapi.model.IRI;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

@ConfigurationProperties
public record CollapseConfig(String ontology, Path outPath, List<String> except, Optional<IRI> newIRI) {

	public boolean isReferenceOf(final HasOntologyID ontology) {
		final var iri = ontology.getOntologyID().getOntologyIRI();
		return iri.isPresent() && iri.get().toString().endsWith(this.ontology + ".owl");
	}

}
