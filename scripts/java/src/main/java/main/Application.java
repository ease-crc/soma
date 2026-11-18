package main;

import main.config.ConfigLoader;

import java.nio.file.Path;

public class Application {

	public static void main(final String... args) throws Exception {
		String versionInfo = null;
		for (final String arg : args) {
			if (arg.startsWith("--versionInfo=")) {
				versionInfo = arg.substring("--versionInfo=".length());
			} else {
				throw new IllegalArgumentException("Unknown argument: " + arg);
			}
		}
		if (versionInfo == null) {
			throw new IllegalArgumentException("Missing --versionInfo=<version>");
		}
		new CIRunner(ConfigLoader.load(Path.of("application.yaml")), versionInfo).run();
	}

}
