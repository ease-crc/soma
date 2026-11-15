package main.reasoning;

import main.GuardingXmlCatalogIriMapper;
import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Binds coverage planning and all reasoner results to one unchanged corpus, including its file inventory. */
public final class ReasoningInputSnapshot {
    public record FileIdentity(String resolvedPath, String sha256) {}
    private final List<Path> directories;
    private final Map<String, FileIdentity> files;

    private ReasoningInputSnapshot(List<Path> directories, Map<String, FileIdentity> files) {
        this.directories = List.copyOf(directories);
        this.files = Collections.unmodifiableMap(new TreeMap<>(files));
    }

    public static ReasoningInputSnapshot capture(List<Path> directories) throws Exception {
        var normalized = directories.stream().map(p -> p.toAbsolutePath().normalize()).toList();
        return new ReasoningInputSnapshot(normalized, read(normalized));
    }

    public Map<String, FileIdentity> files() { return files; }

    public void verify() throws Exception {
        if (!files.equals(read(directories)))
            throw new IOException("Reasoning inputs changed since suite planning; coverage/results are incomplete");
    }

    private static Map<String, FileIdentity> read(List<Path> directories) throws Exception {
        var paths = new LinkedHashSet<Path>();
        for (var directory : directories) {
            try (var inputs = Files.list(directory)) {
                inputs.filter(p -> p.getFileName().toString().endsWith(".owl")).forEach(paths::add);
            }
            var catalog = directory.resolve("catalog-v001.xml");
            paths.add(catalog);
            paths.addAll(new GuardingXmlCatalogIriMapper(catalog.toFile()).entries().values());
        }
        var identities = new TreeMap<String, FileIdentity>();
        for (var path : paths) {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
            identities.put(path.toAbsolutePath().normalize().toString(), new FileIdentity(path.toRealPath().toString(), hash));
        }
        return identities;
    }
}
