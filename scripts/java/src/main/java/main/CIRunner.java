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

	@Autowired
	private ElanCVGenerator elanCVGenerator;

	@Autowired
	private IRINamespaceChecker iriNamespaceChecker;

	// only use if needed
	@Autowired
	private IRINamespaceRewriter iriNamespaceRewriter;

	@Override
	public void run(final String... args) throws Exception {
		final CIRunnable[] toRun = {iriNamespaceChecker, elanCVGenerator, gciRewriter, isDefinedInAdder, versionInfoAdder, collapser, ontologySaver};
		for (final var next : toRun) {
			next.run();
		}
	}

}
