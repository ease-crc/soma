package main;

import org.semanticweb.owlapi.model.OWLOntology;

import java.nio.file.Path;

public record CollapseConfig(String ontology, Path out) {

	public boolean isReferenceOf(final OWLOntology ontology) {
		final var iri = ontology.getOntologyID().getOntologyIRI();
		return iri.isPresent() && iri.get().toString().endsWith(ontology + ".owl");
	}

}
