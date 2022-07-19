package main;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.util.Collection;

@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "ontology")
public record OntologyConfig(Path directory, Collection<CollapseConfig> toCollapse) {

}

