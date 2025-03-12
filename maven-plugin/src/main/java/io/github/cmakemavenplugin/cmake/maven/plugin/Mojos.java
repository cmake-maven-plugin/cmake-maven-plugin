package io.github.cmakemavenplugin.cmake.maven.plugin;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Mojo helper functions.
 */
public final class Mojos
{
	/**
	 * @param mojoExecution an instance of {@code MojoExecution}
	 * @return the current plugin's configuration
	 */
	public static Xpp3Dom getConfiguration(MojoExecution mojoExecution)
	{
		// JSR-330 based injection based on https://vaclavkosar.com/software/Modern-Config-Injection-In-Maven-Plugins
		// and some reverse-engineering.
		return (Xpp3Dom) mojoExecution.getPlugin().getExecutions().get(0).getConfiguration();
	}

	/**
	 * Returns a mandatory Maven property.
	 *
	 * @param project an instance of {@code MavenProject}
	 * @param session an instance of {@code MavenSession}
	 * @param name    the name of the property
	 * @return the value of the property
	 */
	public static String getProperty(MavenProject project, MavenSession session, String name)
	{
		// Respect the order of precedence used by Maven

		// System properties
		String child = session.getSystemProperties().getProperty(name);
		if (child != null)
			return child;

		// Properties in settings.xml
		child = project.getProperties().getProperty(name);
		if (child != null)
			return child;

		// Properties in pom.xml
		child = session.getUserProperties().getProperty(name);

		if (child == null)
			throw new IllegalArgumentException("Property " + name + " must be set");
		return child;
	}

	/**
	 * Launches and waits for a process to complete.
	 *
	 * @param processBuilder the process builder
	 * @param log            the Maven log
	 * @return the process exit code
	 * @throws IOException          if an I/O error occurs while running the process
	 * @throws InterruptedException if the thread was interrupted
	 */
	public static int waitFor(ProcessBuilder processBuilder, Log log)
		throws IOException, InterruptedException
	{
		Process process = processBuilder.redirectErrorStream(true).start();
		try (BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream())))
		{
			while (true)
			{
				String line = in.readLine();
				if (line == null)
					break;
				log.info(line);
			}
		}
		int result = process.waitFor();
		if (result != 0)
		{
			log.warn("Command: " + processBuilder.command());
			log.warn("Directory: " + processBuilder.directory());
			log.warn("Environment: " + processBuilder.environment());
			log.warn("Exit code: " + result);
		}
		return result;
	}

	/**
	 * Prevent construction.
	 */
	private Mojos()
	{
	}
}