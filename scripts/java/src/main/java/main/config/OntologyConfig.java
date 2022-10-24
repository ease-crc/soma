package main.config;

import org.semanticweb.owlapi.model.OWLDocumentFormat;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.util.Collection;


@ConfigurationProperties(prefix = "ontology")
public record OntologyConfig(Path directory, Collection<CollapseConfig> toCollapse, OWLDocumentFormat format) {

}