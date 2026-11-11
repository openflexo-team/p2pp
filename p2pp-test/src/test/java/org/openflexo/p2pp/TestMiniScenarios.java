package org.openflexo.p2pp;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.openflexo.p2pp.mini.MiniTarget;
import org.openflexo.p2pp.testing.ResourceFiles;
import org.openflexo.p2pp.testing.scenario.Scenario;
import org.openflexo.p2pp.testing.scenario.ScenarioFile;
import org.openflexo.p2pp.testing.scenario.ScenarioRunner;

/**
 * Runs every scenario of {@code src/test/resources/scenarios/*.p2pt}, one test each (see {@link Scenario} for the format).
 */
@RunWith(Parameterized.class)
public class TestMiniScenarios {

	@Parameters(name = "{0}")
	public static Collection<Object[]> scenarios() throws IOException {
		List<File> files = ResourceFiles.list(TestMiniScenarios.class, "/scenarios", ".p2pt");
		List<Object[]> returned = new ArrayList<>();
		for (File f : files) {
			for (Scenario s : ScenarioFile.read(f)) {
				returned.add(new Object[] { s.toString(), s });
			}
		}
		return returned;
	}

	private final Scenario scenario;

	public TestMiniScenarios(String name, Scenario scenario) {
		this.scenario = scenario;
	}

	@Test
	public void run() {
		ScenarioRunner.run(new MiniTarget(), scenario);
	}
}
