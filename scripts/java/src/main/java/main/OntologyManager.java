package main;

import main.config.OntologyConfig;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.OntologyCopy;
import java.nio.file.*;
import java.util.*;

/** Owns either loaded sources or independent working ontologies. Construction performs no I/O. */
public final class OntologyManager {
    private final OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
    private final Map<String, OWLOntology> documents = new LinkedHashMap<>();
    private final Map<IRI, String> aliases = new LinkedHashMap<>();

    public OntologyManager() {}

    public void load(OntologyConfig config) throws Exception {
        if (!documents.isEmpty()) throw new IllegalStateException("Ontologies already loaded");
        config.validatePaths();
        var catalog = new GuardingXmlCatalogIriMapper(config.directory().resolve("catalog-v001.xml").toFile());
        manager.getIRIMappers().add(catalog);
        manager.setOntologyLoaderConfiguration(new OWLOntologyLoaderConfiguration()
            .setMissingImportHandlingStrategy(MissingImportHandlingStrategy.THROW_EXCEPTION));
        List<Path> paths;
        try (var files = Files.list(config.directory())) {
            paths = files.filter(p -> p.toString().endsWith(".owl")).sorted().toList();
        }
        if (paths.isEmpty()) throw new IllegalArgumentException("No OWL inputs in " + config.directory());
        for (var path : paths) {
            var document = path.toRealPath();
            var loaded = manager.ontologies().filter(o -> Path.of(manager.getOntologyDocumentIRI(o).toURI()).toAbsolutePath().normalize().equals(document)).findFirst();
            var ontology = loaded.isPresent() ? loaded.get() : manager.loadOntologyFromOntologyDocument(path.toFile());
            if (ontology.getOntologyID().isAnonymous()) throw new IllegalArgumentException("Anonymous ontology: " + path);
            documents.put(path.getFileName().toString(), ontology);
        }
        // Validate imports even when OWLAPI has already cached a matching ontology IRI.
        for (var mapping : catalog.entries().entrySet()) {
            String filename = mapping.getValue().getFileName().toString();
            if (!documents.containsKey(filename)) throw new IllegalArgumentException("Catalog target is not an OWL input: " + mapping);
            if (!Path.of(manager.getOntologyDocumentIRI(documents.get(filename)).toURI()).toRealPath().equals(mapping.getValue()))
                throw new IllegalArgumentException("Catalog target is not a top-level OWL input: " + mapping);
            aliases.put(mapping.getKey(), filename);
        }
        for (var entry : documents.entrySet()) {
            var name = entry.getKey();
            var ontology = entry.getValue();
            var iri = ontology.getOntologyID().getOntologyIRI().orElseThrow();
            String existing = aliases.putIfAbsent(iri, name);
            if (existing != null && !existing.equals(name)) throw new IllegalArgumentException("Catalog maps ontology IRI to wrong document: " + iri);
            for (var declaration : ontology.getImportsDeclarations()) {
                var target = catalog.entries().get(declaration.getIRI());
                if (target == null) throw new IllegalArgumentException("Missing local catalog mapping: " + declaration.getIRI());
                var imported = manager.getImportedOntology(declaration);
                if (imported == null || !Path.of(manager.getOntologyDocumentIRI(imported).toURI()).toRealPath().equals(target))
                    throw new IllegalArgumentException("Import does not match catalog: " + declaration.getIRI());
            }
        }
    }

    public OntologyManager workingCopy() throws OWLOntologyCreationException {
        var copy = new OntologyManager();
        for (var item : documents.entrySet()) {
            var ontology = copy.manager.copyOntology(item.getValue(), OntologyCopy.SHALLOW);
            copy.documents.put(item.getKey(), ontology);
        }
        copy.aliases.putAll(aliases);
        // OWLAPI's source-manager alias cache is not copied. Resolve catalog aliases to
        // canonical ontology IRIs on the working copy, preserving the source import graph.
        for (var ontology : copy.documents.values()) {
            for (var declaration : ontology.getImportsDeclarations()) {
                var target = copy.documents.get(copy.aliases.get(declaration.getIRI()));
                var canonical = target.getOntologyID().getOntologyIRI().orElseThrow();
                if (!canonical.equals(declaration.getIRI())) {
                    copy.manager.applyChange(new RemoveImport(ontology, declaration));
                    copy.manager.applyChange(new AddImport(ontology, copy.manager.getOWLDataFactory().getOWLImportsDeclaration(canonical)));
                }
            }
        }
        return copy;
    }

    public OWLOntology resolve(String reference) {
        var matches = documents.values().stream().filter(o -> main.config.CollapseConfig.containsReferenceOf(List.of(reference), o)).distinct().toList();
        if (matches.size() != 1) throw new IllegalArgumentException("Expected one ontology for " + reference + ", found " + matches.size());
        return matches.getFirst();
    }
    public Map<String, OWLOntology> documents() { return Collections.unmodifiableMap(documents); }
    public Map<IRI, String> aliases() { return Collections.unmodifiableMap(aliases); }
    public OWLOntologyManager getOntologyManager() { return manager; }
}
