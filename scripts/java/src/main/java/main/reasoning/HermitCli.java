package main.reasoning;

import com.fasterxml.jackson.databind.ObjectMapper;
import main.GuardingXmlCatalogIriMapper;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

/** Supervises memory/time-bounded workers and writes reviewable reports, including on failure. */
public final class HermitCli {
    private HermitCli() {}
    private static final ObjectMapper JSON = new ObjectMapper();

    public static void main(String[] args) {
        try { System.exit(execute(args)); }
        catch (Exception e) { System.err.println("HermiT tool error: " + e); System.exit(2); }
    }

    public static int execute(String[] args) throws Exception {
        var options = new HashMap<String, String>();
        for (String arg : args) {
            int separator = arg.indexOf('=');
            if (!arg.startsWith("--") || separator < 3 || separator == arg.length() - 1
                    || options.putIfAbsent(arg.substring(2, separator), arg.substring(separator + 1)) != null)
                throw new IllegalArgumentException("Expected unique --name=value options");
        }
        if (!Set.of("input", "suite", "catalog", "output", "timeout-seconds", "memory-mb", "scope").containsAll(options.keySet())
                || options.containsKey("input") == options.containsKey("suite") || !options.containsKey("output")
                || (options.containsKey("suite") && options.containsKey("catalog"))
                || (options.containsKey("input") && options.containsKey("scope")))
            throw new IllegalArgumentException("Use --input=FILE [--catalog=FILE] or --suite=REPOSITORY, plus --output=DIRECTORY");
        String scope = options.getOrDefault("scope", "roots");
        if (!Set.of("roots", "modules").contains(scope)) throw new IllegalArgumentException("Scope must be roots or modules");
        long timeout = Long.parseLong(options.getOrDefault("timeout-seconds", "600"));
        int memory = Integer.parseInt(options.getOrDefault("memory-mb", "2048"));
        if (timeout <= 0 || memory < 64) throw new IllegalArgumentException("Positive timeout and at least 64 MiB required");
        var output = Path.of(options.get("output")).toAbsolutePath().normalize();
        Files.createDirectories(output);
        var entries = new ArrayList<Map<String, Object>>();
        int exit = 0;
        if (options.containsKey("input")) {
            var input = Path.of(options.get("input")).toAbsolutePath().normalize();
            var catalog = options.containsKey("catalog") ? Path.of(options.get("catalog")) : siblingCatalog(input);
            exit = run(input, catalog, output.resolve("input"), Duration.ofSeconds(timeout), memory).exitCode();
        } else {
            // Replace any previous success before discovery or execution can fail.
            writeSummary(output, entries, false, 2);
            var coverage = new LinkedHashMap<String, ReasoningSuitePlan.Plan>();
            JSON.writerWithDefaultPrettyPrinter().writeValue(output.resolve("coverage.json").toFile(), coverage);
            JSON.writerWithDefaultPrettyPrinter().writeValue(output.resolve("input-snapshot.json").toFile(), Map.of());
            var root = Path.of(options.get("suite")).toAbsolutePath().normalize();
            var snapshot = ReasoningInputSnapshot.capture(List.of(root.resolve("owl"), root.resolve("build/ci/dist/owl")));
            JSON.writerWithDefaultPrettyPrinter().writeValue(output.resolve("input-snapshot.json").toFile(), snapshot.files());
            for (String group : List.of("sources", "exports")) {
                var directory = root.resolve(group.equals("sources") ? "owl" : "build/ci/dist/owl");
                ReasoningSuitePlan.Plan plan;
                if (scope.equals("modules")) {
                    List<String> names;
                    try (var files = Files.list(directory)) {
                        names = files.filter(p -> p.getFileName().toString().endsWith(".owl"))
                                .map(p -> p.getFileName().toString()).sorted().toList();
                    }
                    if (names.isEmpty()) throw new IllegalArgumentException("No OWL inputs in " + directory);
                    var identity = new TreeMap<String, String>();
                    names.forEach(name -> identity.put(name, name));
                    plan = new ReasoningSuitePlan.Plan(names, identity);
                } else {
                    var preferred = List.of(group.equals("sources") ? "SOMA-ALL.owl" : "SOMA.owl", "SOMA-HOME.owl");
                    plan = ReasoningSuitePlan.load(directory, output.resolve("planning-" + group), preferred);
                }
                snapshot.verify();
                coverage.put(group, plan);
                JSON.writerWithDefaultPrettyPrinter().writeValue(output.resolve("coverage.json").toFile(), coverage);
                for (var name : plan.roots()) {
                    snapshot.verify();
                    var input = directory.resolve(name);
                    var reportDirectory = output.resolve(group).resolve(input.getFileName().toString());
                    var report = run(input, directory.resolve("catalog-v001.xml"), reportDirectory, Duration.ofSeconds(timeout), memory);
                    entries.add(Map.of("input", input.toString(), "status", report.status().name(),
                            "report", reportDirectory.resolve("report.json").toString()));
                    exit = Math.max(exit, report.exitCode());
                    writeSummary(output, entries, false, 2);
                    snapshot.verify();
                }
            }
            snapshot.verify();
            writeSummary(output, entries, true, exit);
        }
        return exit;
    }

    private static void writeSummary(Path output, List<Map<String, Object>> entries, boolean complete, int exit) throws Exception {
        JSON.writerWithDefaultPrettyPrinter().writeValue(output.resolve("summary.json").toFile(),
                Map.of("schemaVersion", 1, "complete", complete, "exitCode", exit, "entries", entries));
    }

    private static Path siblingCatalog(Path input) {
        var path = input.resolveSibling("catalog-v001.xml");
        return Files.exists(path) ? path : null;
    }

    public static ReasoningReport run(Path input, Path catalog, Path output, Duration timeout, int memoryMb) throws Exception {
        if (timeout.isZero() || timeout.isNegative() || memoryMb < 64) throw new IllegalArgumentException("Invalid resource limits");
        Files.createDirectories(output);
        Files.deleteIfExists(output.resolve("report.json"));
        var started = Instant.now();
        long start = System.nanoTime();
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("startedAt", started.toString());
        metadata.put("input", input.toAbsolutePath().normalize().toString());
        metadata.put("reasoner", "HermiT");
        var version = new Properties();
        try (var stream = HermitCli.class.getResourceAsStream("/META-INF/maven/net.sourceforge.owlapi/org.semanticweb.hermit/pom.properties")) {
            if (stream == null) throw new IllegalStateException("Missing HermiT dependency version");
            version.load(stream);
        }
        metadata.put("reasonerVersion", version.getProperty("version"));
        metadata.put("owlapiVersion", org.semanticweb.owlapi.util.VersionInfo.getVersionInfo().getVersion());
        metadata.put("javaVersion", System.getProperty("java.version"));
        metadata.put("memoryLimitMiB", memoryMb);
        metadata.put("timeoutMillis", timeout.toMillis());
        ReasoningReport report;
        try {
            var hashes = identity(input, catalog);
            metadata.put("inputFilesSha256", hashes);
            var command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "-Xmx" + memoryMb + "m", "-cp", System.getProperty("java.class.path"), HermitWorker.class.getName(),
                    input.toAbsolutePath().toString()));
            if (catalog != null) command.add(catalog.toAbsolutePath().toString());
            report = ReasoningProcess.run(command, timeout, output);
            if (!hashes.equals(identity(input, catalog))) report = ReasoningReport.toolError("Input changed during reasoning");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            report = ReasoningReport.toolError("Reasoner interrupted");
        } catch (Exception e) { report = ReasoningReport.toolError("Reasoner execution failure: " + e); }
        metadata.put("durationMillis", (System.nanoTime() - start) / 1_000_000);
        var document = new LinkedHashMap<String, Object>();
        document.put("metadata", metadata);
        document.put("status", report.status().name());
        document.put("result", HermitWorker.protocol(report));
        JSON.writerWithDefaultPrettyPrinter().writeValue(output.resolve("report.json").toFile(), document);
        System.out.println(input + ": " + report.status() + " (" + metadata.get("durationMillis") + " ms)");
        return report;
    }

    /** Hash the root, catalog and every allowed import target, a conservative superset of the closure. */
    private static Map<String, String> identity(Path input, Path catalog) throws Exception {
        var paths = new TreeSet<Path>();
        paths.add(input.toRealPath());
        if (catalog != null) {
            paths.add(catalog.toRealPath());
            paths.addAll(new GuardingXmlCatalogIriMapper(catalog.toFile()).entries().values());
        }
        var hashes = new TreeMap<String, String>();
        for (var path : paths) {
            var digest = MessageDigest.getInstance("SHA-256");
            hashes.put(path.toString(), HexFormat.of().formatHex(digest.digest(Files.readAllBytes(path))));
        }
        return hashes;
    }
}
