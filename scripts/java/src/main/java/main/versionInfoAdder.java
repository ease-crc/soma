package main;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.change.SetOntologyIDData;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.ChangeApplied;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.annotation.Priority;
import java.util.Collection;
import java.util.HashSet;

@Component
@Priority(1)
public class versionInfoAdder implements CommandLineRunner {


    /**
     * {@link Logger} of this class.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(versionInfoAdder.class);

    private final OntologyManager ontologyManager;

    @Value("${versionInfo}")
    private String versionInfo;

    @Autowired
    public versionInfoAdder(final OntologyManager ontologyManager) {
        this.ontologyManager = ontologyManager;
    }


    @Override
    public void run(final String... args) {
        for (final OWLOntology ontology : ontologyManager.getOntologyManager().getOntologies()) {
            addVersionInfo(ontology, versionInfo);
        }
    }

    private static void addVersionInfo(final OWLOntology ontology, String version) {

        final var df = OWLManager.getOWLDataFactory();
        final var versionAnnotation = df.getOWLAnnotation(df.getOWLVersionInfo(), df.getOWLLiteral(version));
        ontology.getOWLOntologyManager().applyChange(new AddOntologyAnnotation(ontology, versionAnnotation));

        LOGGER.info("Added versionInfo {} to {}", version, ontology.getOntologyID().getOntologyIRI());
    }


}
