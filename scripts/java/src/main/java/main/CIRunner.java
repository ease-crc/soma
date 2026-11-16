package main;

import main.ci_runners.*;
import main.config.OntologyConfig;

public class CIRunner {

	private final OntologyConfig config;

	private final String versionInfo;

	public CIRunner(final OntologyConfig config, final String versionInfo) {
		this.config = config;
		this.versionInfo = versionInfo;
	}

	public void run() throws Exception {
		final var ontologyManager = new OntologyManager(config);
		// IRINamespaceRewriter is only needed for one-off migrations and is not part of the default run.
		final CIRunnable[] toRun = {
				new IRINamespaceChecker(ontologyManager),
				new SubclassNothingRewriter(ontologyManager),
				new IsDefinedInAdder(ontologyManager),
				new VersionInfoAdder(ontologyManager, versionInfo),
				new Collapser(ontologyManager, config, versionInfo),
				new OntologySaver(ontologyManager, config)
		};
		for (final var next : toRun) {
			next.run();
		}
	}

}
