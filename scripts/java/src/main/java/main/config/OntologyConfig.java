package main.config;

import org.semanticweb.owlapi.model.OWLDocumentFormat;

import java.nio.file.Path;
import java.util.Collection;


public record OntologyConfig(Path directory, Collection<CollapseConfig> toCollapse, OWLDocumentFormat format) {

}