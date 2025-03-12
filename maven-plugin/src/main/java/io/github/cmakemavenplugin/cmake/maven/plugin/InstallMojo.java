package io.github.cmakemavenplugin.cmake.maven.plugin;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

/**
 * Goal which installs project files generated by CMake, equivalent to {@code cmake --install {projectDirectory}}.
 */
@Mojo(name = "install")
public class InstallMojo extends CmakeMojo
{
	/**
	 * The build configuration (e.g. "Win32|Debug", "x64|Release").
	 */
	@Parameter
	private String config;

	/**
	 * The installation prefix, equivalent to {@code cmake --install {projectDirectory} --prefix {prefix}}.
	 */
	@Parameter
	private String prefix;

	/**
	 * Enables CMake verbose mode.
	 */
	@Parameter(defaultValue = "false")
	private boolean verbose;

	/**
	 * The project binary directory to install.
	 */
	@Parameter(defaultValue = "${project.build.directory}/cmake")
	private File projectDirectory;

	/**
	 * Creates a new instance.
	 *
	 * @param project       an instance of {@code MavenProject}
	 * @param pluginManager an instance of {@code PluginManager}
	 * @param session       an instance of {@code MavenSession}
	 */
	@Inject
	public InstallMojo(MavenProject project, MavenSession session, BuildPluginManager pluginManager)
	{
		super(project, session, pluginManager);
	}

	@Override
	public void execute()
		throws MojoExecutionException
	{
		super.execute();
		try
		{
			Path projectPath = projectDirectory.toPath();
			if (Files.notExists(projectPath))
				throw new MojoExecutionException(projectPath.toAbsolutePath() + " does not exist");
			if (!Files.isDirectory(projectPath))
				throw new MojoExecutionException(projectPath.toAbsolutePath() + " must be a directory");

			downloadBinariesIfNecessary();

			ProcessBuilder processBuilder = new ProcessBuilder();
			overrideEnvironmentVariables(processBuilder);

			String cmakePath = getBinaryPath("cmake", processBuilder).toString();
			processBuilder.command().add(cmakePath);

			Collections.addAll(processBuilder.command(), "--install", projectPath.toString());
			if (config != null)
				Collections.addAll(processBuilder.command(), "--config", config);
			if (prefix != null)
				Collections.addAll(processBuilder.command(), "--prefix", prefix);
			if (verbose)
				processBuilder.command().add("--verbose");
			addOptions(processBuilder);

			Log log = getLog();
			if (log.isDebugEnabled())
			{
				log.debug("projectDirectory: " + projectPath);
				log.debug("config: " + config);
				log.debug("Environment: " + processBuilder.environment());
				log.debug("Command-line: " + processBuilder.command());
			}
			int returnCode = Mojos.waitFor(processBuilder, getLog());
			if (returnCode != 0)
				throw new MojoExecutionException("Return code: " + returnCode);
		}
		catch (InterruptedException | IOException e)
		{
			throw new MojoExecutionException("", e);
		}
	}
}