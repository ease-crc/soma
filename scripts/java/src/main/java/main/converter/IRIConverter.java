package main.converter;

import org.jetbrains.annotations.NotNull;
import org.semanticweb.owlapi.model.IRI;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
@ConfigurationPropertiesBinding
public class IRIConverter implements Converter<String, IRI> {

	@Override
	public IRI convert(final @NotNull String from) {
		return IRI.create(from);
	}
}