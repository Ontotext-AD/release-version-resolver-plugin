package hudson.plugins.releaseversionresolver;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import hudson.Util;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.tasks.Shell;
import hudson.util.VariableResolver;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.net.URL;

import static org.junit.Assert.assertEquals;

public class ReleaseVersionResolverTest extends JenkinsRule {

	@Rule
	public JenkinsRule jenkinsRule = new JenkinsRule();

	private final static String POM_FILE = "pom.xml";
	private final static String PATCH = "patch";
	private final static String MINOR = "minor";
	private final static String MAJOR = "major";

	@Test
	public void testPatchVersionIncrement() throws Exception {
		FreeStyleBuild build = preformBuild(PATCH, POM_FILE, true);
		assertEquals("1.0.1", getReleaseVersion(build));
		assertEquals("1.0.1", getDevelopmentVersion(build));
	}

	@Test
	public void testPatchVersionSnapshotsIncrement() throws Exception {
		FreeStyleBuild build = preformBuild(PATCH, POM_FILE, false);
		assertEquals("1.0.0", getReleaseVersion(build));
		assertEquals("1.0.1-SNAPSHOT", getDevelopmentVersion(build));
	}

	@Test
	public void testMinorVersionIncrement() throws Exception {
		FreeStyleBuild build = preformBuild(MINOR, POM_FILE, true);
		assertEquals("1.1.0", getReleaseVersion(build));
		assertEquals("1.1.0", getDevelopmentVersion(build));
	}

	@Test
	public void testMinorSnapshotsVersionIncrement() throws Exception {
		FreeStyleBuild build = preformBuild(MINOR, POM_FILE, false);
		assertEquals("1.1.0", getReleaseVersion(build));
		assertEquals("1.1.1-SNAPSHOT", getDevelopmentVersion(build));
	}

	@Test
	public void testMajorVersionIncrement() throws Exception {
		FreeStyleBuild build = preformBuild(MAJOR, POM_FILE, true);
		assertEquals("2.0.0", getReleaseVersion(build));
		assertEquals("2.0.0", getDevelopmentVersion(build));
	}

	@Test
	public void testMajorSnapshotsVersionIncrement() throws Exception {
		FreeStyleBuild build = preformBuild(MAJOR, POM_FILE, false);
		assertEquals("2.0.0", getReleaseVersion(build));
		assertEquals("2.0.1-SNAPSHOT", getDevelopmentVersion(build));
	}

	@Test
	public void testUnknownBuildType() throws Exception {
		FreeStyleBuild build = preformBuild("ex", POM_FILE, true);
		jenkinsRule.assertBuildStatus(Result.FAILURE, build);
	}

	private String getReleaseVersion(FreeStyleBuild build) throws Exception {
		return getEnvironmentVariable(build, "${RELEASE_VERSION}");
	}

	private String getDevelopmentVersion(FreeStyleBuild build) throws Exception {
		return getEnvironmentVariable(build, "${DEVELOPMENT_VERSION}");
	}

	private FreeStyleBuild preformBuild(String buildType, String pomFile, boolean snapshots)
			throws Exception {
		URL url = Resources.getResource("pom.xml");
		String pom = Resources.toString(url, Charsets.UTF_8);
		FreeStyleProject project = jenkinsRule.jenkins.createProject(FreeStyleProject.class, "test");

		String writeCommand = String.format("echo '%s' > '%s'", pom, pomFile);
		project.getBuildersList().add(new Shell(writeCommand));
		project.getBuildersList().add(
				new ReleaseVersionResolver(buildType, pomFile, snapshots));

		return project.scheduleBuild2(0).get();
	}

	@SuppressWarnings("unchecked")
	private String getEnvironmentVariable(FreeStyleBuild build, String key)
			throws Exception {
		VariableResolver variableResolver = build.getBuildVariableResolver();
		return Util.replaceMacro(key, variableResolver);
	}
}
