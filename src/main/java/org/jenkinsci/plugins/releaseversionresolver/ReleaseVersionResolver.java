package org.jenkinsci.plugins.releaseversionresolver;

import com.github.zafarkhaja.semver.Version;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.VariableResolver;
import net.sf.json.JSONObject;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.jenkinsci.lib.envinject.EnvInjectLogger;
import org.jenkinsci.plugins.envinject.EnvInjectBuilderContributionAction;
import org.jenkinsci.plugins.envinject.service.EnvInjectVariableGetter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class ReleaseVersionResolver extends Builder {

	private final String buildType;
	private final String pomFile;
	private final boolean noSnapshots;

	@DataBoundConstructor
	public ReleaseVersionResolver(String buildType, String pomFile, boolean noSnapshots) {
		this.buildType = buildType;
		this.pomFile = Util.fixEmptyAndTrim(pomFile);
		this.noSnapshots = noSnapshots;
	}

	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,
			BuildListener listener) throws InterruptedException, IOException {
		return resolveReleaseVersions(build, listener);
	}

	private boolean resolveReleaseVersions(AbstractBuild build, BuildListener listener) throws IOException, InterruptedException {
		boolean result = true;

		String currentVersionString = getCurrentVersion(listener, build);
		if (currentVersionString != null) {
			Version releaseVersion = getReleaseVersion(currentVersionString, listener, build);
			Version developmentVersion = getDevelopmentVersion(releaseVersion, listener);
			if (releaseVersion == null) {
				listener.fatalError("Failed to resolve release version");
				result = false;
			} else if (developmentVersion == null) {
				listener.fatalError("Failed to resolve development version");
				result = false;
			} else {
				String message =
						String.format("Final release version [%s] and development version [%s]", releaseVersion,
								developmentVersion);
				listener.getLogger().println(message);

				if (setVersionsAsEnvironmentVariable(releaseVersion.toString(), developmentVersion.toString(), build, listener)) {
				} else {
					result = false;
				}
			}
		} else {
			result = false;
		}

		return result;
	}

	private boolean setVersionsAsEnvironmentVariable(String releaseVersion, String developmentVersion,
			AbstractBuild<?, ?> build, BuildListener listener) {
		boolean result = true;

		EnvInjectLogger logger = new EnvInjectLogger(listener);

		try {
			EnvInjectVariableGetter variableGetter = new EnvInjectVariableGetter();
			Map<String, String> previousEnvVars = variableGetter.getEnvVarsPreviousSteps(build, logger);

			Map<String, String> variables = new HashMap<String, String>(previousEnvVars);
			variables.put("RELEASE_VERSION", releaseVersion);
			variables.put("DEVELOPMENT_VERSION", developmentVersion);

			variables.putAll(build.getBuildVariables());

			build.addAction(new EnvInjectBuilderContributionAction(variables));
		} catch (Exception e) {
			logger.error(
					"Problems occurs on injecting release and developing env vars: " + e.getMessage());
			result = false;
		}
		return result;
	}

	@SuppressWarnings("unchecked")
	private Version getReleaseVersion(String currentVersionString, BuildListener listener, AbstractBuild build) {
		Version result = null;

		VariableResolver variableResolver = build.getBuildVariableResolver();
		String resolvedBuildType = Util.replaceMacro(buildType, variableResolver);

		Version currentVersion = Version.valueOf(currentVersionString);
		if (resolvedBuildType.toLowerCase().equals("major")) {
			result = currentVersion.incrementMajorVersion();
		} else if (resolvedBuildType.toLowerCase().equals("minor")) {
			result = currentVersion.incrementMinorVersion();
		} else if (resolvedBuildType.toLowerCase().equals("patch") || resolvedBuildType.toLowerCase().equals("bug-fix")) {
			if (noSnapshots) {
				result = currentVersion.incrementPatchVersion();
			} else {
				Version.Builder builder = new Version.Builder();
				builder.setNormalVersion(
						String.format("%s.%s.%s", currentVersion.getMajorVersion(), currentVersion.getMinorVersion(),
								currentVersion.getPatchVersion()));
				result = builder.build();
			}
		} else {
			listener.fatalError("Build type [%s] is not recognizable", resolvedBuildType);
		}

		return result;
	}

	private Version getDevelopmentVersion(Version releaseVersion, BuildListener listener) {
		return noSnapshots ? releaseVersion : releaseVersion.incrementPatchVersion("SNAPSHOT");
	}

	private String getCurrentVersion(BuildListener listener, AbstractBuild build) {
		String result = null;

		FilePath workspace = build.getWorkspace();
		FilePath resolvedPomFile = workspace.child(pomFile);

		Model model;
		InputStream reader;
		MavenXpp3Reader mavenXpp3Reader = new MavenXpp3Reader();

		try {
			reader = resolvedPomFile.read();
			model = mavenXpp3Reader.read(reader);
			result = model.getVersion();
		} catch (FileNotFoundException e) {
			listener.fatalError("Unable to find pom file [%s], [%s]", resolvedPomFile,
					e.getMessage());
		} catch (XmlPullParserException e) {
			listener.fatalError("A error occurred during parsing of pom file [%s], [%s]", resolvedPomFile, e.getMessage());
		} catch (IOException e) {
			listener.fatalError("A error occurred during reading of pom file [%s], [%s]", resolvedPomFile, e.getMessage());
		}

		return result;
	}

	public boolean isNoSnapshots() {
		return noSnapshots;
	}

	public String getBuildType() {
		return buildType;
	}

	public String getPomFile() {
		return pomFile;
	}

	@Extension
	public static final class DescriptorImpl extends
			BuildStepDescriptor<Builder> {

		public DescriptorImpl() {
			super(ReleaseVersionResolver.class);
		}

		@Override
		public String getDisplayName() {
			return "Resolve release version";
		}

		@Override
		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			return true;
		}

		@Override
		public Builder newInstance(StaplerRequest req, JSONObject formData)
				throws FormException {
			return req.bindJSON(ReleaseVersionResolver.class, formData);
		}
	}

	@Override
	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl) super.getDescriptor();
	}

}