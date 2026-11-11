package org.openflexo.p2pp.testing;

import java.util.Collections;
import java.util.List;

/**
 * What a language must provide for the p2pp test harness (scenarios, edit matrix) to run on it: how to parse a source, and how to read and
 * edit what was parsed. The harness works on texts only; it knows nothing of the language, nor of p2pp itself.
 * <p>
 * Implemented once per language: for the {@code Mini} fixture language of this module's own tests, and for the real ones (B, FML...).
 */
public interface PrettyPrintTarget {

	/** Parse a source, and give access to its model and pretty-prints. Throws what the parser throws on a syntax error. */
	Document parse(String source) throws Exception;

	/** The comments of a text, each as written: an edit must not lose them. By default the language is said to have none to check. */
	default List<String> comments(String text) {
		return Collections.emptyList();
	}

	/**
	 * The first departure of a text from the canonical layout of the language (indentation, spacing...), described, or null. By default no
	 * layout is checked. The harness only applies it to the results for a source that itself has none.
	 */
	default String layoutViolation(String text) {
		return null;
	}

	/** A parsed source */
	interface Document {

		/** A text standing for the model, as complete as the language needs: two models are the same when their dumps are */
		String dump();

		/** The pretty-print preserving the parsed text where the model did not change */
		String derived();

		/** The pretty-print from scratch */
		String normalized();

		/** Edit the model with a command of the language of edits of the target (see the scenarios), throwing when it cannot be applied */
		void apply(String command);
	}
}
