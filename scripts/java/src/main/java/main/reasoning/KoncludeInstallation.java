package main.reasoning;

import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;

/** Only the official release's native executables are accepted. See KONCLUDE.md for provenance. */
public final class KoncludeInstallation {
    public static final String VERSION = "0.7.0-1138";
    private KoncludeInstallation() {}
    public static String verify(Path binary) throws Exception {
        if (!Files.isRegularFile(binary) || !Files.isExecutable(binary))
            throw new IllegalArgumentException("Missing/non-executable Konclude binary: " + binary);
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(binary)));
        if (!Set.of("0d9bf7dc5b2d6e35e873c1b4fe1ef169b263fb18f6fe7183225ac65b17e31ed7",
                "5484f16dcff71486a5deed9cf9cea8a0f7febf115aaa6915ad2e8c1cf16965e3").contains(hash))
            throw new IllegalArgumentException("Unverified Konclude executable SHA-256: " + hash);
        return hash;
    }
}
