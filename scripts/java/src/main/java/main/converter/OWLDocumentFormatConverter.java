package main.converter;

import org.semanticweb.owlapi.formats.FunctionalSyntaxDocumentFormat;
import org.semanticweb.owlapi.formats.ManchesterSyntaxDocumentFormat;
import org.semanticweb.owlapi.formats.OWLXMLDocumentFormat;
import org.semanticweb.owlapi.formats.RDFXMLDocumentFormat;
import org.semanticweb.owlapi.model.OWLDocumentFormat;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
@ConfigurationPropertiesBinding
public class OWLDocumentFormatConverter implements Converter<String, OWLDocumentFormat> {

	@Override
	public OWLDocumentFormat convert(final String source) {
		return switch (source.toUpperCase()) {
			case "RDF_XML" -> new RDFXMLDocumentFormat();
			case "OWL_XML" -> new OWLXMLDocumentFormat();
			case "FUNCTIONAL" -> new FunctionalSyntaxDocumentFormat();
			case "MANCHESTER" -> new ManchesterSyntaxDocumentFormat();
			default -> throw new IllegalStateException("Unexpected value: " + source);
		};
	}
}
