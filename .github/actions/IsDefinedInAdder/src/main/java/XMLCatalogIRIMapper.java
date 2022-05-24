import java.net.URI;
import java.nio.file.Path;

import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntologyIRIMapper;

import javax.xml.catalog.CatalogFeatures;
import javax.xml.catalog.CatalogManager;
import javax.xml.catalog.CatalogResolver;

public class XMLCatalogIRIMapper implements OWLOntologyIRIMapper {
	private final CatalogResolver catalogResolver;
	private final Path ontologyDirectory;
	
	public XMLCatalogIRIMapper(URI catalogUri, Path ontologyDirectory) {
		this(CatalogManager.catalogResolver(CatalogFeatures.defaults(), catalogUri), ontologyDirectory);
	}
	
	public XMLCatalogIRIMapper(CatalogResolver catalogResolver, final Path ontologyDirectory) {
		this.catalogResolver = catalogResolver;
		this.ontologyDirectory = ontologyDirectory;
	}

	@Override
	public IRI getDocumentIRI(IRI original) {
		var source = catalogResolver.resolve(original.toURI().toString(), ontologyDirectory.toString());
		if (!source.isEmpty()) {
			return IRI.create(source.getSystemId());
		}
		return null;
	}

}