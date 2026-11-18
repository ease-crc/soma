package main;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HermitCheckTest {

	private static HermitCheck.Result check(final String name) throws Exception {
		return HermitCheck.check(new File("src/test/resources/hermit", name));
	}

	@Test
	void passesConsistentOntology() throws Exception {
		assertTrue(check("consistent.ofn").passed());
	}

	@Test
	void reportsInconsistency() throws Exception {
		final var result = check("inconsistent.ofn");
		assertFalse(result.consistent());
		assertFalse(result.passed());
	}

	@Test
	void reportsUnsatisfiableClassesAndProperties() throws Exception {
		final var result = check("unsatisfiable.ofn");
		assertTrue(result.consistent());
		assertEquals(List.of("urn:test#C"), result.unsatisfiableClasses());
		assertEquals(List.of("urn:test#p"), result.unsatisfiableObjectProperties());
	}

	@Test
	void resolvesImportsThroughCatalog() throws Exception {
		assertEquals(List.of("urn:test#C"), check("imports.ofn").unsatisfiableClasses());
	}

	@Test
	void reportsProfileViolationsInsteadOfReasoning() throws Exception {
		final var result = check("not-dl.ofn");
		assertEquals(1, result.profileViolations().size());
		assertTrue(result.profileViolations().getFirst().contains("non-simple property in asymmetric"));
		assertNull(result.consistent());
		assertFalse(result.passed());
	}

}
