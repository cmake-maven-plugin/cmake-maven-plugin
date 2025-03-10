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
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
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
 * Goal which generates project files.
 *
 * @author Gili Tzabari
 */
@Mojo(name = "generate", defaultPhase = LifecyclePhase.PROCESS_SOURCES)
public class GenerateMojo extends CmakeMojo
{
	/**
	 * The directory containing CMakeLists.txt.
	 */
	@Parameter(required = true)
	private File sourcePath;
	/**
	 * The output directory.
	 *
	 * @deprecated use {@link #projectDirectory} instead
	 */
	@Deprecated
	@Parameter
	private File targetPath;
	/**
	 * The output directory.
	 */
	@Parameter(defaultValue = "${project.build.directory}")
	private File projectDirectory;
	/**
	 * The makefile generator to use.
	 */
	@Parameter
	private String generator;

	/**
	 * Creates a new instance.
	 *
	 * @param project       an instance of {@code MavenProject}
	 * @param pluginManager an instance of {@code PluginManager}
	 * @param session       an instance of {@code MavenSession}
	 */
	@Inject
	public GenerateMojo(MavenProject project, BuildPluginManager pluginManager, MavenSession session)
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
			if (!sourcePath.exists())
				throw new MojoExecutionException("sourcePath does not exist: " + sourcePath.getAbsolutePath());
			if (targetPath != null)
			{
				throw new MojoExecutionException("The \"targetPath\" parameter has been renamed. Please use " +
					"\"projectDirectory\" instead.");
			}
			if (projectDirectory == null)
			{
				// TODO: Replace with @Parameter(required = true) after removing deprecated parameters
				PluginDescriptor pluginDescriptor = (PluginDescriptor) getPluginContext().get("pluginDescriptor");
				throw new MojoExecutionException("The parameters 'projectDirectory' for goal " +
					pluginDescriptor.getGroupId() + ":" + pluginDescriptor.getArtifactId() + ":" +
					pluginDescriptor.getVersion() + ":" + pluginDescriptor.getName() + " are missing or invalid");
			}
			Path projectPath = projectDirectory.toPath();
			Files.createDirectories(projectPath);

			downloadBinariesIfNecessary();

			ProcessBuilder processBuilder = new ProcessBuilder().directory(projectDirectory);
			overrideEnvironmentVariables(processBuilder);

			String cmakePath = getBinaryPath("cmake", processBuilder).toString();
			processBuilder.command().add(cmakePath);

			if (generator != null && !generator.trim().isEmpty())
				Collections.addAll(processBuilder.command(), "-G", generator);

			addOptions(processBuilder);
			processBuilder.command().add(sourcePath.getAbsolutePath());

			Log log = getLog();
			if (log.isDebugEnabled())
			{
				log.debug("sourcePath: " + sourcePath);
				log.debug("projectDirectory: " + projectPath);
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