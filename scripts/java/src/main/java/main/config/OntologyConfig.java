package main.config;

import org.semanticweb.owlapi.model.OWLDocumentFormat;

import java.nio.file.Path;
import java.util.Collection;


public record OntologyConfig(Path directory, Path outputDirectory, Collection<CollapseConfig> toCollapse,
                             OWLDocumentFormat format) {

}