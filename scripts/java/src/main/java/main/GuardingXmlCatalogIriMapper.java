package main;

import org.protege.xmlcatalog.CatalogUtilities;
import org.protege.xmlcatalog.XMLCatalog;
import org.protege.xmlcatalog.entry.Entry;
import org.protege.xmlcatalog.redirect.UriRedirectVisitor;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntologyIRIMapper;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.Iterator;

public class GuardingXmlCatalogIriMapper implements OWLOntologyIRIMapper {
    private XMLCatalog catalog;

    public GuardingXmlCatalogIriMapper(File f) throws MalformedURLException, IOException {
        this(CatalogUtilities.parseDocument(f.toURI().toURL()));
    }

    public GuardingXmlCatalogIriMapper(XMLCatalog catalog) {
        this.catalog = catalog;
    }

    public IRI getDocumentIRI(IRI original) {
        UriRedirectVisitor visitor = new UriRedirectVisitor(original.toURI());

        for (Entry subEntry : catalog.getEntries()) {
            subEntry.accept(visitor);
            if (visitor.getRedirect() != null) {
                return IRI.create(visitor.getRedirect());
            }
        }
        throw new RuntimeException("You are trying to load an ontology from an online source, but we decided to self-host them, e.g, DUL. The ontology you are trying to load is: " + original);
    }
}
