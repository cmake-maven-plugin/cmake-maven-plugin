/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Collections;

/**
 * Goal which runs CMake/CTest tests.
 *
 * @author <a href="mailto:ksclarke@gmail.com">Kevin S. Clarke</a>
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
	 */
	@Parameter(property = "ctest.build.dir", required = true)
	private File buildDirectory;
	/**
	 * Value that lets Maven tests fail without causing the build to fail.
	 */
	@Parameter(defaultValue = "false")
	private boolean ignoreTestFailure;
	/**
	 * Maven tests value that indicates just the ctest tests are to be skipped.
	 */
	@Parameter(property = "ctest.skip.tests", defaultValue = "false")
	private boolean ctestSkip;
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
	 */
	@Parameter(property = "dashboard")
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

		// Surefire skips tests with properties, so we'll do it this way too
		if (skipTests || ctestSkip)
		{
			if (log.isInfoEnabled())
				log.info("Tests are skipped.");
			return;
		}
		String buildDir = buildDirectory.getAbsolutePath();
		if (!buildDirectory.exists())
			throw new MojoExecutionException(buildDir + " does not exist");
		if (!buildDirectory.isDirectory())
			throw new MojoExecutionException(buildDir + " isn't directory");

		if (threadCount == 0)
			threadCount = Runtime.getRuntime().availableProcessors();

		try
		{
			downloadBinariesIfNecessary();

			ProcessBuilder processBuilder = new ProcessBuilder().directory(buildDirectory);
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
				log.debug("CTest build directory: " + buildDir);
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
			File tagFile = new File(buildDirectory, "/Testing/TAG");
			Charset charset = Charset.defaultCharset();
			StreamSource source = getStreamSource(tagFile, charset);
			File reportsDir = new File(getBuildDirectory(), "surefire-reports");
			File xmlReport = new File(reportsDir, "CTestResults.xml");
			StreamResult result = new StreamResult(xmlReport);

			// We have to create if there aren't other Surefire tests
			if (!reportsDir.exists())
				if (!reportsDir.mkdirs())
					throw new IOException("Couldn't create " + reportsDir);

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

	private StreamSource getStreamSource(File tagFile, Charset charset) throws IOException
	{
		FileInputStream fis = new FileInputStream(tagFile);
		InputStreamReader isr = new InputStreamReader(fis, charset);
		BufferedReader tagReader = new BufferedReader(isr);
		String tag;
		try
		{
			tag = tagReader.readLine();
		}
		finally
		{
			tagReader.close();
		}

		if (tag == null || tag.trim().isEmpty())
			throw new IOException("Couldn't read ctest TAG file");

		// Get the current run's test data for reformatting
		String xmlTestFilePath = "/Testing/" + tag + "/Test.xml";
		File xmlSource = new File(buildDirectory, xmlTestFilePath);
		return new StreamSource(xmlSource);
	}
}