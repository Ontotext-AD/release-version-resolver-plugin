package org.jenkinsci.plugins.releaseversionresolver;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import hudson.EnvVars;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.CaptureEnvironmentBuilder;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.SingleFileSCM;

import java.net.URL;

import static org.junit.Assert.assertEquals;

public class ReleaseVersionResolverTest extends JenkinsRule {

	@Rule
	public JenkinsRule jenkinsRule = new JenkinsRule();
	private FreeStyleProject project;

	private final static String POM_FILE = "pom.xml";
	private final static String PATCH = "patch";
	private final static String MINOR = "minor";
	private final static String MAJOR = "major";

	@Before public void setUp() throws Exception {
		project = jenkinsRule.jenkins.createProject(FreeStyleProject.class, "project");

		URL url = Resources.getResource("pom.xml");
		String pom = Resources.toString(url, Charsets.UTF_8);
		project.setScm(new SingleFileSCM(POM_FILE, pom));
	}

	@Test
	public void testPatchVersionIncrement() throws Exception {
		this.project.getBuildersList().add(new ReleaseVersionResolver(PATCH, POM_FILE, true));
		assertEquals("1.0.1", buildEnvVars().get("RELEASE_VERSION"));
		assertEquals("1.0.1", buildEnvVars().get("DEVELOPMENT_VERSION"));

	}

	@Test
	public void testPatchVersionSnapshotsIncrement() throws Exception {
		this.project.getBuildersList().add(new ReleaseVersionResolver(PATCH, POM_FILE, false));
		assertEquals("1.0.0", buildEnvVars().get("RELEASE_VERSION"));
		assertEquals("1.0.1-SNAPSHOT", buildEnvVars().get("DEVELOPMENT_VERSION"));
	}

	@Test
	public void testMinorVersionIncrement() throws Exception {
		this.project.getBuildersList().add(new ReleaseVersionResolver(MINOR, POM_FILE, true));
		assertEquals("1.1.0", buildEnvVars().get("RELEASE_VERSION"));
		assertEquals("1.1.0", buildEnvVars().get("DEVELOPMENT_VERSION"));
	}

	@Test
	public void testMinorSnapshotsVersionIncrement() throws Exception {
		this.project.getBuildersList().add(new ReleaseVersionResolver(MINOR, POM_FILE, false));
		assertEquals("1.1.0", buildEnvVars().get("RELEASE_VERSION"));
		assertEquals("1.1.1-SNAPSHOT", buildEnvVars().get("DEVELOPMENT_VERSION"));
	}

	@Test
	public void testMajorVersionIncrement() throws Exception {
		this.project.getBuildersList().add(new ReleaseVersionResolver(MAJOR, POM_FILE, true));
		assertEquals("2.0.0", buildEnvVars().get("RELEASE_VERSION"));
		assertEquals("2.0.0", buildEnvVars().get("DEVELOPMENT_VERSION"));
	}

	@Test
	public void testMajorSnapshotsVersionIncrement() throws Exception {
		this.project.getBuildersList().add(new ReleaseVersionResolver(MAJOR, POM_FILE, false));
		assertEquals("2.0.0", buildEnvVars().get("RELEASE_VERSION"));
		assertEquals("2.0.1-SNAPSHOT", buildEnvVars().get("DEVELOPMENT_VERSION"));
	}

	@Test
	public void testUnknownBuildType() throws Exception {
		this.project.getBuildersList().add(new ReleaseVersionResolver("ex", POM_FILE, true));
		jenkinsRule.assertBuildStatus(Result.FAILURE, project.scheduleBuild2(0).get());
	}

	@Test
	public void testWrongPomNameBuildType() throws Exception {
		this.project.getBuildersList().add(new ReleaseVersionResolver(PATCH, "not-here.pom", true));
		jenkinsRule.assertBuildStatus(Result.FAILURE, project.scheduleBuild2(0).get());
	}

	private EnvVars buildEnvVars() throws Exception {
		CaptureEnvironmentBuilder capture = new CaptureEnvironmentBuilder();
		project.getBuildersList().add(capture);

		project.scheduleBuild2(0).get();
		final EnvVars envVars = capture.getEnvVars();
		return envVars;
	}
}
