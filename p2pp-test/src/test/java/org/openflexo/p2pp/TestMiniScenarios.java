package org.openflexo.p2pp;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.openflexo.p2pp.scenario.Scenario;
import org.openflexo.p2pp.scenario.ScenarioFile;
import org.openflexo.p2pp.scenario.ScenarioRunner;

/**
 * Runs every scenario of {@code src/test/resources/scenarios/*.p2pt}, one test each (see {@link Scenario} for the format).
 */
@RunWith(Parameterized.class)
public class TestMiniScenarios {

	@Parameters(name = "{0}")
	public static Collection<Object[]> scenarios() throws IOException {
		File[] files = new File(TestMiniScenarios.class.getResource("/scenarios").getFile()).listFiles((dir, name) -> name.endsWith(".p2pt"));
		Arrays.sort(files);
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
		ScenarioRunner.run(scenario);
	}
}
