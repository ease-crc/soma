package main;

import main.reasoning.ReasoningInputSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ReasoningInputSnapshotTest {
    @TempDir Path temp;

    void catalog() throws Exception {
        Files.writeString(temp.resolve("catalog-v001.xml"), "<catalog xmlns=\"urn:oasis:names:tc:entity:xmlns:xml:catalog\"/>");
    }

    @Test void editsToSkippedModulesAndNewModulesInvalidateCoverageSnapshot() throws Exception {
        catalog();
        var module = temp.resolve("covered-module.owl");
        Files.writeString(module, "initial");
        var snapshot = ReasoningInputSnapshot.capture(List.of(temp));
        assertDoesNotThrow(snapshot::verify);
        Files.writeString(module, "changed after coverage planning, before root reasoning");
        assertThrows(java.io.IOException.class, snapshot::verify);
        var next = ReasoningInputSnapshot.capture(List.of(temp));
        Files.writeString(temp.resolve("new-uncovered.owl"), "new module");
        assertThrows(java.io.IOException.class, next::verify);
    }

    @Test void catalogEditsInvalidateSnapshotEvenWhenTargetsAreUnchanged() throws Exception {
        catalog();
        var snapshot = ReasoningInputSnapshot.capture(List.of(temp));
        Files.writeString(temp.resolve("catalog-v001.xml"), "<catalog xmlns=\"urn:oasis:names:tc:entity:xmlns:xml:catalog\"><!-- changed --></catalog>");
        assertThrows(java.io.IOException.class, snapshot::verify);
    }
}
