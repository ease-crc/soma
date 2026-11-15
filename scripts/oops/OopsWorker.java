import com.fasterxml.jackson.databind.ObjectMapper;
import es.upm.fi.oeg.oops.*;
import main.reasoning.LocalReasoningOntology;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.rdf.model.ModelFactory;
import org.semanticweb.owlapi.formats.RDFXMLDocumentFormat;
import org.semanticweb.owlapi.apibinding.OWLManager;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Runs the pinned upstream rules without its web application or network loader. */
public final class OopsWorker {
    public static void main(String[] args) throws Exception {
        // OWLAPI 5 pulls in an old Xerces provider without ACCESS_EXTERNAL_DTD support.
        // Keep the shared strict catalog loader on the JDK's secure XML implementation.
        System.setProperty("javax.xml.parsers.DocumentBuilderFactory",
            javax.xml.parsers.DocumentBuilderFactory.newDefaultInstance().getClass().getName());
        var selected = Arrays.stream(args[3].split(",")).map(Integer::valueOf).toList();
        var ontology = LocalReasoningOntology.load(Path.of(args[0]), args[1].isEmpty() ? null : Path.of(args[1]));
        var manager = OWLManager.createOWLOntologyManager();
        var merged = manager.createOntology();
        for (var member : ontology.getImportsClosure()) {
            manager.addAxioms(merged, member.getAxioms());
            for (var annotation : member.getAnnotations())
                manager.applyChange(new org.semanticweb.owlapi.model.AddOntologyAnnotation(merged, annotation));
        }
        var bytes = new ByteArrayOutputStream();
        manager.saveOntology(merged, new RDFXMLDocumentFormat(), bytes);
        var spec = new OntModelSpec(OntModelSpec.OWL_MEM);
        var documents = new org.apache.jena.ontology.OntDocumentManager();
        documents.setProcessImports(false);
        spec.setDocumentManager(documents);
        var model = ModelFactory.createOntologyModel(spec);
        model.read(new ByteArrayInputStream(bytes.toByteArray()), null, "RDF/XML");
        var source = new SrcModel(new SrcSpec(SrcType.RDF_CODE, null, "", null), model, merged);
        var checkers = CheckersCatalogue.getCheckers(selected.stream().map(CheckerId::new).toList());
        var actual = checkers.stream().map(c -> c.getInfo().getId().getNumeral()).sorted().toList();
        if (!actual.equals(selected.stream().sorted().toList()))
            throw new IllegalStateException("Missing or duplicate upstream checkers: " + actual);
        // Direct calls avoid the upstream RDF renderer, which cannot render every pitfall type.
        var findings = new TreeMap<String, Map<String, Object>>();
        var warnings = new TreeSet<String>();
        var completed = new ArrayList<Integer>();
        for (var checker : checkers) {
            System.err.println("Checking " + checker.getInfo().getId());
            var context = new CheckingContext(source, ModelFactory.createDefaultModel());
            checker.check(context);
            for (var group : context.getResults().values()) for (var pitfall : group) {
                var info = pitfall.getInfo();
                var resources = new TreeSet<String>();
                for (var resource : pitfall.getResources()) {
                    var affected = context.getOutputModel().listObjectsOfProperty(resource,
                        context.getOutputModel().createProperty(Linter.NS_OOPS_DEF + "hasAffectedElement")).toList();
                    if (affected.isEmpty()) {
                        resources.add(resource.isURIResource() ? resource.getURI() : "[anonymous]");
                    } else for (var node : affected) {
                        resources.add(node.isURIResource() ? node.asResource().getURI() : "[anonymous]");
                    }
                }
                var finding = new TreeMap<String, Object>();
                finding.put("code", info.getId().toString());
                finding.put("name", info.getTitle());
                finding.put("description", info.getExplanation());
                finding.put("importance", info.getImportance().toString());
                finding.put("resources", resources);
                findings.put(info.getId() + resources.toString(), finding);
            }
            for (var warning : context.getWarnings()) warnings.add(warning.toString());
            completed.add(checker.getInfo().getId().getNumeral());
        }
        Collections.sort(completed);
        var report = new TreeMap<String, Object>();
        report.put("completed_rules", completed);
        report.put("findings", new ArrayList<>(findings.values()));
        report.put("warnings", warnings);
        report.put("java_version", System.getProperty("java.version"));
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(Path.of(args[2]).toFile(), report);
        model.close();
    }
}
