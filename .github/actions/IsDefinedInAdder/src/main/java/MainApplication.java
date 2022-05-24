import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication
public final class MainApplication implements CommandLineRunner {

	private MainApplication() {
	}

	public static void main(final String... args) {
		// final ApplicationContext applicationContext =
				new SpringApplicationBuilder(MainApplication.class)
				.run(args);
		// TODO close application context
	}


	@Override
	public void run(final String... args) {

	}
}
