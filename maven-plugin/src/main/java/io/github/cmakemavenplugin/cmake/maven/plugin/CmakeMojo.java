package io.github.cmakemavenplugin.cmake.maven.plugin;

import io.github.cmakemavenplugin.cmake.common.Platform;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.twdata.maven.mojoexecutor.MojoExecutor;
import org.twdata.maven.mojoexecutor.MojoExecutor.Element;
import org.twdata.maven.mojoexecutor.MojoExecutor.ExecutionEnvironment;

import javax.inject.Inject;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class CmakeMojo extends AbstractMojo
{
	/**
	 * {@code true} if this plugin should download and unpack cmake binaries. {@code false} if {@code cmake} is
	 * already installed and is on the {@code PATH}. The default value is {@code true} on platforms that ship
	 * with the cmake binaries and {@code false} on platforms that do not.
	 */
	@Parameter(property = "cmake.download")
	private Boolean downloadBinaries;
	/**
	 * The directory containing the cmake executable. By default, it is assumed that the executable is on
	 * the PATH. This parameter is ignored if {@link #downloadBinaries} is {@code true}.
	 */
	@Parameter(property = "cmake.dir")
	private String cmakeDir;
	/**
	 * The environment variables.
	 */
	@Parameter
	private Map<String, String> environmentVariables;
	/**
	 * Extra command-line options to pass to cmake or ctest.
	 */
	@Parameter
	private List<String> options;

	protected final MavenProject project;
	private final BuildPluginManager pluginManager;
	private final MavenSession session;
	private final Platform platform = Platform.detected();

	/**
	 * Creates a new instance.
	 *
	 * @param project       an instance of {@code MavenProject}
	 * @param session       an instance of {@code MavenSession}
	 * @param pluginManager an instance of {@code PluginManager}
	 */
	@Inject
	public CmakeMojo(MavenProject project, MavenSession session, BuildPluginManager pluginManager)
	{
		this.project = project;
		this.pluginManager = pluginManager;
		this.session = session;
	}

	@Override
	public void execute() throws MojoExecutionException
	{
		if (downloadBinaries == null)
			downloadBinaries = !platform.shipsWithBinaries();
	}

	/**
	 * Downloads cmake if necessary.
	 *
	 * @throws MojoExecutionException if the download fails
	 */
	protected void downloadBinariesIfNecessary() throws MojoExecutionException
	{
		Log log = getLog();
		log.debug("downloadBinaries: " + downloadBinaries);
		if (!downloadBinaries)
			return;
		Path outputDirectory = Paths.get(project.getBuild().getDirectory(), "dependency/cmake");
		downloadBinaries(outputDirectory);
	}

	/**
	 * Downloads cmake.
	 *
	 * @param outputDirectory the directory to download into
	 * @throws MojoExecutionException if the download fails
	 */
	private void downloadBinaries(Path outputDirectory)
		throws MojoExecutionException
	{
		getLog().info("Downloading binaries to " + outputDirectory);
		PluginDescriptor pluginDescriptor = (PluginDescriptor) getPluginContext().get("pluginDescriptor");
		String groupId = pluginDescriptor.getGroupId();
		String version = pluginDescriptor.getVersion();
		String binariesArtifact = "cmake-binaries";
		Element groupIdElement = new Element("groupId", groupId);
		Element artifactIdElement = new Element("artifactId", binariesArtifact);
		Element versionElement = new Element("version", version);
		Element classifierElement = new Element("classifier", platform.getClassifier());
		Element outputDirectoryElement = new Element("outputDirectory", outputDirectory.toString());
		Element artifactItemElement = new Element("artifactItem", groupIdElement, artifactIdElement,
			versionElement, classifierElement, outputDirectoryElement);
		Element artifactItemsItem = new Element("artifactItems", artifactItemElement);
		Xpp3Dom configuration = MojoExecutor.configuration(artifactItemsItem);
		ExecutionEnvironment environment = MojoExecutor.executionEnvironment(project, session, pluginManager);
		Plugin dependencyPlugin = MojoExecutor.plugin("org.apache.maven.plugins",
			"maven-dependency-plugin", "3.6.1");
		MojoExecutor.executeMojo(dependencyPlugin, "unpack", configuration, environment);
	}

	/**
	 * @param filename       the filename of the binary
	 * @param processBuilder the {@code ProcessBuilder}
	 * @return the command-line arguments for running the binary
	 * @throws FileNotFoundException if the binary was not found
	 */
	protected Path getBinaryPath(String filename, ProcessBuilder processBuilder) throws FileNotFoundException
	{
		Log log = getLog();
		Path cmakeDir = getCmakeDir();
		if (cmakeDir == null)
		{
			log.info("Executing " + filename + " on PATH");
			String path = platform.getEnvironment(processBuilder, "PATH");
			if (path == null)
			{
				throw new IllegalArgumentException("PATH not found\n" +
					"env: " + processBuilder.environment());
			}
			return platform.getExecutableOnPath(filename, path);
		}
		Path result = cmakeDir.resolve(filename + platform.getExecutableSuffix());
		log.info("Executing " + result);
		return result;
	}

	/**
	 * Returns the directory containing the cmake binaries.
	 *
	 * @return {@code null} if cmake should be executed from the PATH
	 */
	private Path getCmakeDir()
	{
		Log log = getLog();
		log.debug("downloadBinaries: " + downloadBinaries);
		if (downloadBinaries)
		{
			Path outputDirectory = Paths.get(project.getBuild().getDirectory(), "dependency/cmake");
			return outputDirectory.resolve("bin");
		}
		if (cmakeDir == null)
			return null;
		return Paths.get(cmakeDir);
	}

	/**
	 * Adds command-line options to the processBuilder.
	 *
	 * @param processBuilder the {@code ProcessBuilder}
	 */
	protected void addOptions(ProcessBuilder processBuilder)
	{
		if (options == null)
			return;
		// Skip undefined Maven properties:
		// <options>
		//   <option>${optional.property}</option>
		// </options>
		List<String> nonEmptyOptions = options.stream().filter(option -> !option.isEmpty()).
			collect(Collectors.toList());
		processBuilder.command().addAll(nonEmptyOptions);
	}

	/**
	 * Overrides environment variables in the {@code ProcessBuilder}.
	 *
	 * @param processBuilder the {@code ProcessBuilder}
	 */
	protected void overrideEnvironmentVariables(ProcessBuilder processBuilder)
	{
		if (environmentVariables == null)
			return;
		platform.overrideEnvironmentVariables(environmentVariables, processBuilder);
	}
}