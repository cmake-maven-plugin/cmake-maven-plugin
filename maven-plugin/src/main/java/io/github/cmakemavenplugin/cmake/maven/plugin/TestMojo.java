package io.github.cmakemavenplugin.cmake.maven.plugin;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import javax.inject.Inject;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

/**
 * Goal which runs CMake/CTest tests.
 */
@Mojo(name = "test", defaultPhase = LifecyclePhase.TEST)
public class TestMojo extends CmakeMojo
{
	/**
	 * The test configuration (e.g. "Win32|Debug", "x64|Release").
	 */
	@Parameter
	private String config;

	/**
	 * The directory to run ctest in.
	 *
	 * @deprecated use {@link #projectDirectory} instead
	 */
	@Deprecated
	@Parameter
	private File buildDirectoryDeprecatedParameter;
	/**
	 * The directory to run ctest in.
	 *
	 * @deprecated use {@code cmake.build.directory} instead
	 */
	@Deprecated
	@Parameter(property = "ctest.build.dir")
	private File buildDirectoryDeprecatedProperty;

	/**
	 * The directory to run ctest in.
	 */
	@Parameter(defaultValue = "${project.build.directory}/cmake")
	private File projectDirectory;
	/**
	 * Value that lets Maven tests fail without causing the build to fail.
	 */
	@Parameter(defaultValue = "false")
	private boolean ignoreTestFailure;

	/**
	 * Maven tests value that indicates just the ctest tests are to be skipped.
	 *
	 * @deprecated use {@link #skipTests} instead
	 */
	@Deprecated
	@Parameter(defaultValue = "false")
	private boolean ctestSkipDeprecatedParameter;

	/**
	 * Maven tests value that indicates just the ctest tests are to be skipped.
	 *
	 * @deprecated use {@code maven.test.skip} instead
	 */
	@Deprecated
	@Parameter(property = "ctest.skip.tests")
	private boolean ctestSkipDeprecatedProperty;

	/**
	 * Standard Maven tests value that indicates all tests are to be skipped.
	 */
	@Parameter(property = "maven.test.skip", defaultValue = "false")
	private boolean skipTests;
	/**
	 * Number of threads to use; if not specified, uses
	 * <code>Runtime.getRuntime().availableProcessors()</code>.
	 */
	@Parameter(property = "threadCount", defaultValue = "0")
	private int threadCount;

	/**
	 * The dashboard to which results should be submitted. This is configured through the optional
	 * CTestConfig.cmake file.
	 *
	 * @deprecated use {@code cmake.dashboard} instead
	 */
	@Deprecated
	@Parameter(property = "dashboard")
	private String dashboardDeprecatedProperty;

	/**
	 * The dashboard to which results should be submitted. This is configured through the optional
	 * CTestConfig.cmake file.
	 */
	@Parameter(property = "cmake.dashboard")
	private String dashboard;

	/**
	 * Creates a new instance.
	 *
	 * @param project       an instance of {@code MavenProject}
	 * @param session       an instance of {@code MavenSession}
	 * @param pluginManager an instance of {@code PluginManager}
	 */
	@Inject
	public TestMojo(MavenProject project, MavenSession session, BuildPluginManager pluginManager)
	{
		super(project, session, pluginManager);
	}

	/**
	 * Executes the CTest run.
	 *
	 * @throws MojoExecutionException if an unexpected problem occurs
	 */
	@Override
	public void execute() throws MojoExecutionException
	{
		super.execute();
		Log log = getLog();

		if (buildDirectoryDeprecatedParameter != null)
		{
			throw new MojoExecutionException("The \"buildDirectory\" parameter has been renamed. Please use " +
				"\"projectDirectory\" instead.");
		}
		if (buildDirectoryDeprecatedProperty != null)
		{
			throw new MojoExecutionException("The \"ctest.build.dir\" property has been replaced by " +
				"${project.build.directory}.");
		}
		if (ctestSkipDeprecatedParameter)
			throw new MojoExecutionException("The \"ctestSkip\" parameter has been replaced by ${skipTests}.");
		if (ctestSkipDeprecatedProperty)
		{
			throw new MojoExecutionException("The \"ctest.skip.tests\" parameter has replaced by " +
				"${maven.test.skip}.");
		}
		if (dashboardDeprecatedProperty != null)
		{
			throw new MojoExecutionException("The \"dashboard\" parameter has been renamed. Please use " +
				"\"cmake.dashboard\" instead.");
		}

		// Surefire skips tests with properties, so we'll do it this way too
		if (skipTests)
		{
			if (log.isInfoEnabled())
				log.info("Tests are skipped.");
			return;
		}
		String projectPath = projectDirectory.getAbsolutePath();
		if (!projectDirectory.exists())
			throw new MojoExecutionException(projectPath + " does not exist");
		if (!projectDirectory.isDirectory())
			throw new MojoExecutionException(projectPath + " isn't directory");

		if (threadCount == 0)
			threadCount = Runtime.getRuntime().availableProcessors();

		try
		{
			downloadBinariesIfNecessary();

			ProcessBuilder processBuilder = new ProcessBuilder().directory(projectDirectory);
			overrideEnvironmentVariables(processBuilder);

			String ctestPath = getBinaryPath("ctest", processBuilder).toString();
			processBuilder.command().add(ctestPath);

			Collections.addAll(processBuilder.command(), "--test-action", "Test", "--output-on-failure");

			String threadCountString = Integer.toString(threadCount);
			Collections.addAll(processBuilder.command(), "--parallel", threadCountString);
			if (config != null)
				Collections.addAll(processBuilder.command(), "--build-config", config);

			// If set, this will post results to a pre-configured dashboard
			if (dashboard != null)
				Collections.addAll(processBuilder.command(), "-D", dashboard);

			addOptions(processBuilder);

			if (log.isDebugEnabled())
			{
				log.debug("projectDirectory: " + projectPath);
				log.debug("Number of threads used: " + threadCount);
				log.debug("Environment: " + processBuilder.environment());
				log.debug("Command-line: " + processBuilder.command());
			}

			// Run the ctest suite of tests
			int returnCode = Mojos.waitFor(processBuilder, getLog());

			// Convert ctest xml output to junit xml for better integration
			InputStream stream = TestMojo.class.getResourceAsStream("/ctest2junit.xsl");
			TransformerFactory tf = TransformerFactory.newInstance();
			StreamSource xsltSource = new StreamSource(stream);
			Transformer transformer = tf.newTransformer(xsltSource);

			// Read the ctest TAG file to find out what current run was called
			Path tagFile = projectDirectory.toPath().resolve("Testing/TAG");
			Charset charset = Charset.defaultCharset();
			StreamSource source = getStreamSource(tagFile, charset);
			Path reportsDirectory = Paths.get(project.getBuild().getDirectory(), "surefire-reports");
			Path xmlReport = reportsDirectory.resolve("CTestResults.xml");
			StreamResult result = new StreamResult(xmlReport.toFile());

			// We have to create if there aren't other Surefire tests
			Files.createDirectories(reportsDirectory);

			// Transform CTest output into Surefire style test output
			transformer.transform(source, result);

			if (returnCode != 0)
			{
				if (ignoreTestFailure)
					log.warn("ignoreTestFailure is true. Ignoring failure");
				else
					throw new MojoExecutionException("Return code: " + returnCode);
			}
		}
		catch (InterruptedException | IOException | TransformerException e)
		{
			throw new MojoExecutionException(e.getMessage(), e);
		}
	}

	private StreamSource getStreamSource(Path tagFile, Charset charset) throws IOException
	{
		String tag;
		try (BufferedReader reader = Files.newBufferedReader(tagFile, charset))
		{
			tag = reader.readLine();
		}
		if (tag == null || tag.trim().isEmpty())
			throw new IOException("Couldn't read ctest TAG file");

		// Get the current run's test data for reformatting
		Path xmlSource = projectDirectory.toPath().resolve("Testing/" + tag + "/Test.xml");
		return new StreamSource(xmlSource.toFile());
	}
}