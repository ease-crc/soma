package main.reasoning;

import org.semanticweb.HermiT.ReasonerFactory;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.Imports;
import java.nio.file.*;
import java.util.List;

/** Local-only OWLAPI adapter. A fresh manager and reasoner are used for each entry point. */
public final class HermitEvaluator {
    private HermitEvaluator() {}

    public static ReasoningReport evaluate(Path input, Path catalogPath) {
        try {
            var ontology = LocalReasoningOntology.load(input, catalogPath);
            var manager = ontology.getOWLOntologyManager();
            var reasoner = new ReasonerFactory().createReasoner(ontology);
            try {
                if (!reasoner.isConsistent()) return new ReasoningReport(true, false,
                        List.of(), List.of(), List.of(), List.of(), List.of());
                var df = manager.getOWLDataFactory();
                return new ReasoningReport(true, true, List.of(),
                        reasoner.getUnsatisfiableClasses().entities().map(c -> c.getIRI().toString()).toList(),
                        ontology.objectPropertiesInSignature(Imports.INCLUDED).filter(p -> !p.isOWLBottomObjectProperty())
                                .filter(p -> !reasoner.isSatisfiable(df.getOWLObjectSomeValuesFrom(p, df.getOWLThing())))
                                .map(p -> p.getIRI().toString()).toList(),
                        ontology.dataPropertiesInSignature(Imports.INCLUDED).filter(p -> !p.isOWLBottomDataProperty())
                                .filter(p -> !reasoner.isSatisfiable(df.getOWLDataSomeValuesFrom(p, df.getTopDatatype())))
                                .map(p -> p.getIRI().toString()).toList(), List.of());
            } finally { reasoner.dispose(); }
        } catch (Exception e) {
            return ReasoningReport.toolError("Load/reasoner failure: " + e);
        }
    }
}
