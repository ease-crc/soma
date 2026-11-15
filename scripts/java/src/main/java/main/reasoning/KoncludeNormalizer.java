package main.reasoning;

import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.util.OWLObjectTransformer;

/** Exact logical equivalence, also for non-simple roles: at least one is an existential. */
public final class KoncludeNormalizer {
    private KoncludeNormalizer() {}
    public static int normalize(OWLOntology ontology) {
        var manager = ontology.getOWLOntologyManager();
        var df = manager.getOWLDataFactory();
        var transformer = new OWLObjectTransformer<OWLClassExpression>(object -> true,
                expression -> expression instanceof OWLObjectMinCardinality minimum && minimum.getCardinality() == 1
                        ? df.getOWLObjectSomeValuesFrom(minimum.getProperty(), minimum.getFiller()) : expression,
                df, OWLClassExpression.class);
        int replacements = 0;
        while (true) {
            var changes = transformer.change(ontology);
            if (changes.isEmpty()) return replacements;
            replacements += (int) changes.stream().filter(OWLOntologyChange::isAddAxiom).count();
            manager.applyChanges(changes);
        }
    }
}
