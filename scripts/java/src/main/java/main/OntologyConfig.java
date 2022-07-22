package main;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.util.Collection;


@ConfigurationProperties(prefix = "ontology")
public record OntologyConfig(Path directory, Collection<CollapseConfig> toCollapse) {

}