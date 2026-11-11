package org.openflexo.p2pp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.openflexo.p2pp.mini.EditGenerator;
import org.openflexo.p2pp.mini.MiniCorpus;
import org.openflexo.p2pp.mini.MiniParseException;
import org.openflexo.p2pp.mini.MiniTarget;
import org.openflexo.p2pp.testing.matrix.EditCase;
import org.openflexo.p2pp.testing.matrix.EditMatrix;

/**
 * The edit matrix of Mini: every atomic edit of the model of every source of the corpus (see {@link EditGenerator}), checked against the
 * invariants of the harness (see {@link EditMatrix}). One test per kind of edit and position ({@code add-member/first},
 * {@code remove-param/only}...).
 * <p>
 * The groups that show a known defect are listed in {@code matrix-known-defects.txt}.
 */
@RunWith(Parameterized.class)
public class TestMiniEditMatrix {

	@Parameters(name = "{0}")
	public static Collection<Object[]> groups() throws IOException, MiniParseException {
		List<EditCase> cases = new ArrayList<>();
		for (Map.Entry<String, String> source : MiniCorpus.load().entrySet()) {
			cases.addAll(EditGenerator.generate(source.getKey(), source.getValue()));
		}
		return EditMatrix.group(cases);
	}

	private final String signature;
	private final List<EditCase> cases;

	public TestMiniEditMatrix(String signature, List<EditCase> cases) {
		this.signature = signature;
		this.cases = cases;
	}

	@Test
	public void invariantsHold() throws Exception {
		EditMatrix.run(new MiniTarget(), signature, cases, EditMatrix.knownDefects(TestMiniEditMatrix.class, "/matrix-known-defects.txt").get(signature));
	}
}
