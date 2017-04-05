package com.googlecode.cmakemavenproject;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.ar.ArArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorInputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * Downloads and installs the CMake binaries into the local Maven repository.
 *
 * @author Gili Tzabari
 */
@Mojo(name = "get-binaries", defaultPhase = LifecyclePhase.GENERATE_RESOURCES)
public class GetBinariesMojo
	extends AbstractMojo
{
	/**
	 * The maximum number of times to retry deleting files.
	 */
	private static final int MAX_RETRIES = 30;
	/**
	 * The set of valid classifiers.
	 */
	private static final Set<String> VALID_CLASSIFIERS = ImmutableSet.of("windows-x86_32",
		"windows-x86_64", "linux-x86_32", "linux-x86_64", "linux-arm_32", "mac-x86_64");

	/**
	 * The release platform.
	 */
	@SuppressFBWarnings("UWF_UNWRITTEN_FIELD")
	@Parameter(property = "classifier", readonly = true, required = true)
	private String classifier;
	/**
	 * The project version.
	 */
	@SuppressFBWarnings("UWF_UNWRITTEN_FIELD")
	@Parameter(property = "project.version")
	private String projectVersion;
	@SuppressFBWarnings("UWF_UNWRITTEN_FIELD")
	@Parameter(property = "project", required = true, readonly = true)
	private MavenProject project;

	@Override
	@SuppressFBWarnings("NP_UNWRITTEN_FIELD")
	public void execute()
		throws MojoExecutionException
	{
		String suffix;

		switch (classifier)
		{
			case "windows-x86_32":
			{
				suffix = "win32-x86.zip";
				break;
			}
			case "windows-x86_64":
			{
				suffix = "win64-x64.zip";
				break;
			}
			case "linux-x86_64":
			{
				suffix = "Linux-x86_64.tar.gz";
				break;
			}
			case "mac-x86_64":
			{
				suffix = "Darwin-x86_64.tar.gz";
				break;
			}
			case "linux-x86_32":
			case "linux-arm_32":
			default:
				throw new MojoExecutionException("\"classifier\" must be one of " + VALID_CLASSIFIERS +
					"\nActual: " + classifier);
		}

		String cmakeVersion = getCMakeVersion(projectVersion);
		final Path target = Paths.get(project.getBuild().getDirectory(), "dependency/cmake");
		try
		{
			String majorVersion = getMajorVersion(cmakeVersion);
			Path archive = download(new URL("https://cmake.org/files/v" + majorVersion + "/cmake-" +
				cmakeVersion + "-" + suffix));
			if (Files.notExists(target.resolve("bin")))
			{
				deleteRecursively(target);

				// Directories not normalized, begin by unpacking the binaries
				Log log = getLog();
				if (log.isInfoEnabled())
					log.info("Extracting " + archive + " to " + target);
				extract(archive, target);
				normalizeDirectories(target);
			}
		}
		catch (IOException e)
		{
			throw new MojoExecutionException("", e);
		}
	}

	/**
	 * Returns the cmake version associated with the project.
	 * <p>
	 * @param version the project version
	 * @return the cmake version
	 * @throws NullPointerException     if version is null
	 * @throws IllegalArgumentException if version is empty or has an unexpected format
	 */
	private String getCMakeVersion(String version)
	{
		Preconditions.checkNotNull(version, "version may not be null");
		Preconditions.checkArgument(!version.isEmpty(), "version may not be empty");

		Pattern pattern = Pattern.compile("^(.*?)-.+");
		Matcher matcher = pattern.matcher(version);
		if (!matcher.find())
			throw new IllegalArgumentException("Unexpected version format: " + version);
		return matcher.group(1);
	}

	/**
	 * Returns the major version number of a version.
	 *
	 * @param version the full version number
	 * @return the major version number
	 * @throws NullPointerException     if version is null
	 * @throws IllegalArgumentException if version is empty or has an unexpected format
	 */
	private String getMajorVersion(String version)
	{
		Preconditions.checkNotNull(version, "version may not be null");
		Preconditions.checkArgument(!version.isEmpty(), "version may not be empty");

		Pattern pattern = Pattern.compile("^[\\d]*\\.[\\d]*");
		Matcher matcher = pattern.matcher(version);
		if (!matcher.find())
			throw new IllegalArgumentException("Unexpected version format: " + version);
		return matcher.group();
	}

	/**
	 * Downloads a file.
	 * <p>
	 * @param url the file to download
	 * @return the path of the downloaded file
	 * @throws MojoExecutionException if an error occurs downloading the file
	 */
	private Path download(URL url) throws MojoExecutionException
	{
		String filename = new File(url.getPath()).getName();
		Path result = Paths.get(project.getBuild().getDirectory(), filename);
		try
		{
			if (Files.notExists(result))
			{
				Log log = getLog();
				if (log.isInfoEnabled())
					log.info("Downloading: " + url.toString());
				HttpURLConnection connection = (HttpURLConnection) url.openConnection();

				try (BufferedInputStream in = new BufferedInputStream(connection.getInputStream()))
				{
					Files.createDirectories(Paths.get(project.getBuild().getDirectory()));
					try (BufferedOutputStream out = new BufferedOutputStream(Files.newOutputStream(result)))
					{
						byte[] buffer = new byte[10 * 1024];
						while (true)
						{
							int count = in.read(buffer);
							if (count == -1)
								break;
							out.write(buffer, 0, count);
						}
					}
				}
				finally
				{
					connection.disconnect();
				}
			}
			return result;
		}
		catch (IOException e)
		{
			throw new MojoExecutionException("", e);
		}
	}

	/**
	 * Extracts the contents of an archive.
	 *
	 * @param source the file to extract
	 * @param target the directory to extract to
	 * @throws IOException if an I/O error occurs
	 */
	private void extract(Path source, Path target) throws IOException
	{
		ByteBuffer buffer = ByteBuffer.allocate(10 * 1024);
		try
		{
			extractCompressor(source, target, buffer);
		}
		catch (IOException e)
		{
			if (!(e.getCause() instanceof CompressorException))
				throw e;

			// Perhaps the file is an archive
			extractArchive(source, target, buffer);
		}
	}

	/**
	 * Extracts the contents of an archive.
	 *
	 * @param source the file to extract
	 * @param target the directory to extract to
	 * @param buffer the buffer used to transfer data from source to target
	 * @throws IOException if an I/O error occurs
	 */
	private void extractArchive(Path source, Path target, ByteBuffer buffer) throws IOException
	{
		Path tempDir = Files.createTempDirectory("cmake");
		FileAttribute<?>[] attributes;
		try (ArchiveInputStream in = new ArchiveStreamFactory().createArchiveInputStream(
			new BufferedInputStream(Files.newInputStream(source))))
		{
			if (supportsPosix(in))
				attributes = new FileAttribute<?>[1];
			else
				attributes = new FileAttribute<?>[0];
			while (true)
			{
				ArchiveEntry entry = in.getNextEntry();
				if (entry == null)
					break;
				if (!in.canReadEntryData(entry))
				{
					getLog().warn("Unsupported entry type for " + entry.getName() + ", skipping...");
					long remaining = entry.getSize();
					while (remaining > 0)
					{
						long actual = in.skip(entry.getSize());
						if (actual <= 0)
							throw new AssertionError("skip() returned " + actual);
						remaining -= actual;
					}
					continue;
				}
				if (attributes.length > 0)
					attributes[0] = PosixFilePermissions.asFileAttribute(getPosixPermissions(entry));
				if (entry.isDirectory())
				{
					Path directory = tempDir.resolve(entry.getName());
					Files.createDirectories(directory);

					if (attributes.length > 0)
					{
						@SuppressWarnings("unchecked")
						Set<PosixFilePermission> permissions = (Set<PosixFilePermission>) attributes[0].value();
						Files.setPosixFilePermissions(directory, permissions);
					}
					continue;
				}
				ReadableByteChannel reader = Channels.newChannel(in);
				Path targetFile = tempDir.resolve(entry.getName());

				// Omitted directories are created using the default permissions
				Files.createDirectories(targetFile.getParent());
				try (SeekableByteChannel out = Files.newByteChannel(targetFile,
					ImmutableSet.of(StandardOpenOption.CREATE,
						StandardOpenOption.TRUNCATE_EXISTING,
						StandardOpenOption.WRITE), attributes))
				{
					while (true)
					{
						int count = reader.read(buffer);
						if (count == -1)
							break;
						buffer.flip();
						do
						{
							out.write(buffer);
						}
						while (buffer.hasRemaining());
						buffer.clear();
					}
				}
			}

			// Copy extracted files from tempDir to target.
			// Can't use Files.move() because tempDir might reside on a different drive than target
			copyDirectory(tempDir, target);
			deleteRecursively(tempDir);
		}
		catch (ArchiveException e)
		{
			throw new IOException("Could not uncompress: " + source, e);
		}
	}

	/**
	 * Copies a directory.
	 * <p>
	 * NOTE: This method is not thread-safe.
	 *
	 * @param source the directory to copy from
	 * @param target the directory to copy into
	 * @throws IOException if an I/O error occurs
	 */
	private void copyDirectory(final Path source, final Path target) throws IOException
	{
		Files.walkFileTree(source, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE,
			new FileVisitor<Path>()
		{
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
				throws IOException
			{
				Files.createDirectories(target.resolve(source.relativize(dir)));
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException
			{
				Files.copy(file, target.resolve(source.relativize(file)),
					StandardCopyOption.COPY_ATTRIBUTES);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFileFailed(Path file, IOException e) throws IOException
			{
				throw e;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException e) throws IOException
			{
				if (e != null)
					throw e;
				return FileVisitResult.CONTINUE;
			}
		});
	}

	/**
	 * Extracts the contents of an archive.
	 *
	 * @param source the file to extract
	 * @param target the directory to extract to
	 * @throws IOException if an I/O error occurs
	 */
	private void extractCompressor(Path source, Path target, ByteBuffer buffer) throws IOException
	{
		String filename = source.getFileName().toString();
		String extension = getFileExtension(filename);
		String nameWithoutExtension = filename.substring(0, filename.length() - extension.length());
		String nextExtension = getFileExtension(nameWithoutExtension);
		try (CompressorInputStream in = new CompressorStreamFactory().createCompressorInputStream(
			new BufferedInputStream(Files.newInputStream(source))))
		{
			Path tempDir = Files.createTempDirectory("cmake");
			ReadableByteChannel reader = Channels.newChannel(in);
			Path intermediateTarget = tempDir.resolve(nameWithoutExtension);
			try (SeekableByteChannel out = Files.newByteChannel(intermediateTarget,
				ImmutableSet.of(StandardOpenOption.CREATE,
					StandardOpenOption.TRUNCATE_EXISTING,
					StandardOpenOption.WRITE)))
			{
				while (true)
				{
					int count = reader.read(buffer);
					if (count == -1)
						break;
					buffer.flip();
					do
					{
						out.write(buffer);
					}
					while (buffer.hasRemaining());
					buffer.clear();
				}
			}
			if (!nextExtension.isEmpty())
			{
				extract(intermediateTarget, target);
				deleteRecursively(tempDir);
			}
			else
			{
				Files.createDirectories(target.getParent());
				Files.move(tempDir, target);
			}
		}
		catch (CompressorException e)
		{
			throw new IOException("Could not uncompress: " + source, e);
		}
	}

	/**
	 * @param in the InputStream associated with the archive
	 * @return true if the platform and archive supports POSIX attributes
	 */
	private boolean supportsPosix(InputStream in)
	{
		switch (classifier)
		{
			case "windows-x86_32":
			case "windows-x86_64":
				return false;
			case "linux-x86_32":
			case "linux-x86_64":
			case "linux-arm_32":
			case "mac-x86_64":
				break;
			default:
				throw new AssertionError("\"classifier\" must be one of " + VALID_CLASSIFIERS +
					"\nActual: " + classifier);
		}
		return in instanceof ArchiveInputStream || in instanceof ZipArchiveInputStream ||
			in instanceof TarArchiveInputStream;
	}

	/**
	 * Converts an integer mode to a set of PosixFilePermissions.
	 *
	 * @param entry the archive entry
	 * @return the PosixFilePermissions, or null if the default permissions should be used
	 * @see http://stackoverflow.com/a/9445853/14731
	 */
	private Set<PosixFilePermission> getPosixPermissions(ArchiveEntry entry)
	{
		int mode;
		if (entry instanceof ArArchiveEntry)
		{
			ArArchiveEntry arEntry = (ArArchiveEntry) entry;
			mode = arEntry.getMode();
		}
		else if (entry instanceof ZipArchiveEntry)
		{
			ZipArchiveEntry zipEntry = (ZipArchiveEntry) entry;
			mode = zipEntry.getUnixMode();
		}
		else if (entry instanceof TarArchiveEntry)
		{
			TarArchiveEntry tarEntry = (TarArchiveEntry) entry;
			mode = tarEntry.getMode();
		}
		else
		{
			throw new IllegalArgumentException(entry.getClass().getName() +
				" does not support POSIX permissions");
		}
		StringBuilder result = new StringBuilder(9);

		// Extract digits from left to right
		//
		// REFERENCE: http://stackoverflow.com/questions/203854/how-to-get-the-nth-digit-of-an-integer-with-bit-wise-operations
		for (int i = 3; i >= 1; --i)
		{
			// Octal is base-8
			mode %= Math.pow(8, i);
			int digit = (int) (mode / Math.pow(8, i - 1));
			if ((digit & 0x04) != 0)
				result.append("r");
			else
				result.append("-");
			if ((digit & 0x02) != 0)
				result.append("w");
			else
				result.append("-");
			if ((digit & 0x01) != 0)
				result.append("x");
			else
				result.append("-");
		}
		return PosixFilePermissions.fromString(result.toString());
	}

	/**
	 * Returns a filename extension. For example, {@code getFileExtension("foo.tar.gz")} returns
	 * {@code .gz}. Unix hidden files (e.g. ".hidden") have no extension.
	 *
	 * @param filename the filename
	 * @return an empty string if no extension is found
	 * @throws NullArgumentException if filename is null
	 */
	private String getFileExtension(String filename)
	{
		Preconditions.checkNotNull(filename, "filename may not be null");

		Pattern pattern = Pattern.compile("[^\\.]+(\\.[\\p{Alnum}]+)$");
		Matcher matcher = pattern.matcher(filename);
		if (!matcher.find())
			return "";
		return matcher.group(1);
	}

	/**
	 * Normalize the directory structure across all platforms.
	 *
	 * @param source the binary path
	 * @throws IOException if an I/O error occurs
	 */
	private void normalizeDirectories(final Path source) throws IOException
	{
		final Path[] topDirectory = new Path[1];
		Files.walkFileTree(source, new SimpleFileVisitor<Path>()
		{
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
				throws IOException
			{
				if (dir.getFileName().toString().equals("bin"))
				{
					topDirectory[0] = dir.getParent().toAbsolutePath();
					return FileVisitResult.TERMINATE;
				}
				return FileVisitResult.CONTINUE;
			}
		});
		if (topDirectory[0] == null)
			throw new IOException("Could not find \"bin\" in: " + source);

		Files.walkFileTree(source, new SimpleFileVisitor<Path>()
		{
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException
			{
				if (topDirectory[0].startsWith(file.getParent()))
				{
					// Skip paths outside topDirectory
					return FileVisitResult.CONTINUE;
				}
				Files.move(file, source.resolve(topDirectory[0].relativize(file)),
					StandardCopyOption.ATOMIC_MOVE);
				return super.visitFile(file, attrs);
			}

			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws
				IOException
			{
				if (topDirectory[0].startsWith(dir))
				{
					// Skip paths outside topDirectory
					return FileVisitResult.CONTINUE;
				}
				Files.move(dir, source.resolve(topDirectory[0].relativize(dir)),
					StandardCopyOption.ATOMIC_MOVE);
				return FileVisitResult.SKIP_SUBTREE;
			}
		});
		deleteRecursively(topDirectory[0]);
	}

	/**
	 * Deletes a path recursively.
	 *
	 * @param path the path to delete
	 * @throws IOException if an I/O error occurs
	 */
	private void deleteRecursively(Path path) throws IOException
	{
		// This method is vulnerable to race-conditions but it's the best we can do.
		//
		// BUG: http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=7148952
		if (Files.notExists(path))
			return;
		Files.walkFileTree(path, new SimpleFileVisitor<Path>()
		{
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException
			{
				Files.deleteIfExists(file);
				return super.visitFile(file, attrs);
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException t) throws IOException
			{
				if (t == null)
				{
					for (int i = 0; true; ++i)
					{
						try
						{
							Files.deleteIfExists(dir);
							break;
						}
						catch (DirectoryNotEmptyException e)
						{
							if (i < MAX_RETRIES)
							{
								// Workaround file lock preventing deletion on Windows
								long timeout = Math.min(1000, (long) (10 * Math.pow(2, i)));
								try
								{
									Log log = getLog();
									if (log.isInfoEnabled())
									{
										log.info(dir + " is locked... Sleeping before retry [" + (i + 1) + "/" +
											MAX_RETRIES + "]");
									}
									TimeUnit.MILLISECONDS.sleep(timeout);
									continue;
								}
								catch (InterruptedException ignore)
								{
									// give up
								}
							}
							throw e;
						}
					}
				}
				return super.postVisitDirectory(dir, t);
			}
		});
	}
}
