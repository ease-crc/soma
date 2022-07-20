package main;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({CollapseConfig.class,OntologyConfig.class})
public class Application {

	public static void main(final String... args) {
		// final ApplicationContext applicationContext =
		final var context = new SpringApplicationBuilder(Application.class).run(args);
		context.close();
	}

}
