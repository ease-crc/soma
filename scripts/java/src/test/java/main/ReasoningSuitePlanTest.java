package main;

import main.reasoning.ReasoningSuitePlan;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ReasoningSuitePlanTest {
    @Test void importsAreCoveredWhilePublicRootsAndNewPropertySignaturesAreRetained() throws Exception {
        var manager = OWLManager.createOWLOntologyManager();
        var df = manager.getOWLDataFactory();
        var module = manager.createOntology(IRI.create("urn:plan:module"));
        var main = manager.createOntology(IRI.create("urn:plan:main"));
        var home = manager.createOntology(IRI.create("urn:plan:home"));
        var separate = manager.createOntology(IRI.create("urn:plan:separate"));
        manager.addAxiom(module, df.getOWLSubClassOfAxiom(df.getOWLClass(IRI.create("urn:plan:A")), df.getOWLNothing()));
        manager.applyChange(new AddImport(main, df.getOWLImportsDeclaration(module.getOntologyID().getOntologyIRI().orElseThrow())));
        manager.applyChange(new AddImport(home, df.getOWLImportsDeclaration(main.getOntologyID().getOntologyIRI().orElseThrow())));
        manager.addAxiom(separate, df.getOWLDeclarationAxiom(df.getOWLDataProperty(IRI.create("urn:plan:extra"))));
        var plan = ReasoningSuitePlan.select(Map.of("Module.owl", module, "Main.owl", main,
                "Home.owl", home, "Separate.owl", separate), List.of("Main.owl", "Home.owl"));
        assertEquals(List.of("Main.owl", "Home.owl", "Separate.owl"), plan.roots());
        assertEquals("Main.owl", plan.coveredBy().get("Module.owl"));
        assertEquals(4, plan.coveredBy().size());
    }

    @Test void aggregationCoverageIgnoresAxiomAnnotations() throws Exception {
        var manager = OWLManager.createOWLOntologyManager();
        var df = manager.getOWLDataFactory();
        var root = manager.createOntology(IRI.create("urn:plan:root"));
        var module = manager.createOntology(IRI.create("urn:plan:part"));
        var axiom = df.getOWLSubClassOfAxiom(df.getOWLClass(IRI.create("urn:plan:A")), df.getOWLClass(IRI.create("urn:plan:B")));
        manager.addAxiom(root, axiom.getAnnotatedAxiom(Set.of(df.getOWLAnnotation(df.getRDFSLabel(), df.getOWLLiteral("root")))));
        manager.addAxiom(module, axiom.getAnnotatedAxiom(Set.of(df.getOWLAnnotation(df.getRDFSLabel(), df.getOWLLiteral("module")))));
        var plan = ReasoningSuitePlan.select(Map.of("Root.owl", root, "Module.owl", module), List.of("Root.owl"));
        assertEquals(List.of("Root.owl"), plan.roots());
        assertEquals("Root.owl", plan.coveredBy().get("Module.owl"));
    }

    @Test void separateRootTheoriesMustNotBeUnionedToClaimCoverage() throws Exception {
        var manager = OWLManager.createOWLOntologyManager();
        var df = manager.getOWLDataFactory();
        var left = manager.createOntology(IRI.create("urn:plan:left"));
        var right = manager.createOntology(IRI.create("urn:plan:right"));
        var combined = manager.createOntology(IRI.create("urn:plan:combined"));
        var a = df.getOWLClass(IRI.create("urn:plan:A"));
        var b = df.getOWLClass(IRI.create("urn:plan:B"));
        manager.addAxiom(left, df.getOWLSubClassOfAxiom(a, b));
        manager.addAxiom(right, df.getOWLDisjointClassesAxiom(a, b));
        manager.addAxiom(right, df.getOWLClassAssertionAxiom(a, df.getOWLNamedIndividual(IRI.create("urn:plan:x"))));
        manager.addAxioms(combined, left.getAxioms());
        manager.addAxioms(combined, right.getAxioms());
        var plan = ReasoningSuitePlan.select(Map.of("Left.owl", left, "Right.owl", right, "Combined.owl", combined),
                List.of("Left.owl", "Right.owl"));
        assertEquals(List.of("Left.owl", "Right.owl", "Combined.owl"), plan.roots());
    }
}
