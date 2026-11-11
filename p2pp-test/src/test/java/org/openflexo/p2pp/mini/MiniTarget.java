package org.openflexo.p2pp.mini;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openflexo.p2pp.testing.PrettyPrintTarget;

/**
 * Mini as a language of the p2pp test harness: the first {@link PrettyPrintTarget}, which the harness is validated on.
 */
public class MiniTarget implements PrettyPrintTarget {

	private static final Pattern COMMENT = Pattern.compile("//[^\n]*|/\\*.*?\\*/", Pattern.DOTALL);

	@Override
	public Document parse(String source) throws MiniParseException {
		MiniDocument doc = MiniDocument.parse(source);
		return new Document() {
			@Override
			public String dump() {
				return doc.getUnit().dump();
			}

			@Override
			public String derived() {
				return doc.derived();
			}

			@Override
			public String normalized() {
				return doc.normalized();
			}

			@Override
			public void apply(String command) {
				MiniEdits.apply(doc.getUnit(), command);
			}
		};
	}

	@Override
	public List<String> comments(String text) {
		List<String> returned = new ArrayList<>();
		Matcher m = COMMENT.matcher(text);
		while (m.find()) {
			returned.add(m.group());
		}
		return returned;
	}

	@Override
	public String layoutViolation(String text) {
		return MiniLayout.violation(text);
	}
}
