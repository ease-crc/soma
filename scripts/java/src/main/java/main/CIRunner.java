package main;

import main.ci_runners.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class CIRunner implements CommandLineRunner {

	@Autowired
	private Collapser collapser;

	@Autowired
	private IsDefinedInAdder isDefinedInAdder;

	@Autowired
	private VersionInfoAdder versionInfoAdder;

	@Autowired
	private OntologySaver ontologySaver;

	@Autowired
	private SubclassNothingRewriter gciRewriter;


	@Override
	public void run(final String... args) throws Exception {
		//		final CIRunnable[] toRun = {isDefinedInAdder, versionInfoAdder, collapser, ontologySaver};
		final CIRunnable[] toRun = {gciRewriter, ontologySaver};
		for (final var next : toRun) {
			next.run();
		}
	}

}
