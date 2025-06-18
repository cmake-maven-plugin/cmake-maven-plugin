package io.github.cmakemavenplugin.cmake.common;

import com.github.cowwoc.pouch.core.ConcurrentLazyReference;
import com.github.cowwoc.pouch.core.Reference;
import org.apache.commons.compress.archivers.ArchiveInputStream;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

/**
 * A combination of a system's architecture and operating system.
 */
public final class Platform
{
	private static final Reference<Platform> DETECTED = ConcurrentLazyReference.create(() ->
	{
		OperatingSystem operatingSystem = OperatingSystem.detected();
		Architecture architecture = Architecture.detected();
		return new Platform(operatingSystem, architecture);
	});

	/**
	 * The operating system of the platform.
	 */
	public final OperatingSystem operatingSystem;
	/**
	 * The architecture of the platform.
	 */
	public final Architecture architecture;

	/**
	 * Returns the detected platform.
	 *
	 * @return the detected platform
	 */
	public static Platform detected()
	{
		return DETECTED.getValue();
	}

	/**
	 * Determines if the platform ships with the cmake binaries.
	 *
	 * @return {@code true} if the platform ships with the binaries
	 */
	public boolean shipsWithBinaries()
	{
		switch (architecture)
		{
			case X86_32:
			case X86_64:
			case AARCH_64:
				return true;
			default:
				return false;
		}
	}

	/**
	 * @return {@code true} if the platform binaries are available on
	 * <a href="https://cmake.org/download/">CMake's website</a>
	 */
	public boolean isDownloadAvailable()
	{
		return !((operatingSystem == OperatingSystem.LINUX && architecture == Architecture.ARM_32) ||
			operatingSystem == OperatingSystem.FREEBSD);
	}

	/**
	 * Returns the classifier associated with this platform.
	 *
	 * @return the classifier
	 * @throws UnsupportedOperationException if <a href="https://cmake.org/download/">CMake's website</a>
	 *                                       does not provide binaries for this platform
	 */
	public String getClassifier()
	{
		switch (operatingSystem)
		{
			case LINUX:
				return "linux-" + architecture.name().toLowerCase(Locale.ENGLISH);
			case OSX:
				switch (architecture)
				{
					case X86_64:
					case ARM_32:
					case AARCH_64:
						return "mac-universal";
					default:
						throw new UnsupportedOperationException("Unsupported platform: " + getName());
				}
			case WINDOWS:
				switch (architecture)
				{
					case X86_64:
						return "windows-x86_64";
					case AARCH_64:
						return "windows-aarch_64";
					default:
						throw new UnsupportedOperationException("Unsupported platform: " + getName());
				}
			case FREEBSD:
			{
				throw new UnsupportedOperationException("Platform requires CMake to be on the PATH.\n" +
					"Platform: " + getName() + "\n" +
					"PATH    : " + System.getenv("PATH"));
			}
			default:
				throw new UnsupportedOperationException("Unsupported platform: " + getName());
		}
	}

	/**
	 * @return the name of the platform
	 */
	private static String getName()
	{
		return System.getProperty("os.name") + "/" + System.getProperty("os.arch");
	}

	/**
	 * Returns the fully qualified path of the executable.
	 *
	 * @param filename the filename of a binary
	 * @param path     the {@code PATH} environment variable
	 * @return the fully qualified path of the executable
	 * @throws NullPointerException  if any of the arguments are null
	 * @throws FileNotFoundException if the binary could not be found
	 */
	public Path getExecutableOnPath(String filename, String path) throws FileNotFoundException
	{
		if (filename == null)
			throw new NullPointerException("filename may not be null");
		if (path == null)
			throw new NullPointerException("path may not be null");
		// Per https://stackoverflow.com/a/34061154/14731 it's easier to invoke a fully qualified path
		// than trying to quote command-line arguments properly.
		// https://stackoverflow.com/a/32827512/14731 shows how this can be done.
		String suffix = getExecutableSuffix();
		for (String dirname : path.split(File.pathSeparator))
		{
			// Strip leading/trailing quotes
			dirname = dirname.trim();
			if (dirname.length() >= 2 &&
				(dirname.startsWith("\"") && dirname.endsWith("\"")) ||
				(dirname.startsWith("'") && dirname.endsWith("'")))
			{
				dirname = dirname.substring(1, dirname.length() - 1);
			}
			Path result = Paths.get(dirname, filename + suffix);
			if (Files.isRegularFile(result) && Files.isExecutable(result))
				return result;
		}
		throw new FileNotFoundException(filename + " not found on PATH: " + path);
	}

	/**
	 * Returns the suffix to append to the cmake executables.
	 *
	 * @return the suffix
	 * @throws UnsupportedOperationException if <a href="https://cmake.org/download/">CMake's website</a>
	 *                                       does not provide binaries for this platform
	 */
	public String getExecutableSuffix()
	{
		switch (operatingSystem)
		{
			case LINUX:
			case OSX:
			case FREEBSD:
				return "";
			case WINDOWS:
				return ".exe";
			default:
				throw new UnsupportedOperationException("Unsupported platform: " + getName());
		}
	}

	/**
	 * Returns the suffix to append to the cmake download filename.
	 *
	 * @return the suffix to append to the cmake download filename
	 * @throws UnsupportedOperationException if <a href="https://cmake.org/download/">CMake's website</a>
	 *                                       does not provide binaries for this platform
	 */
	public String getDownloadSuffix()
	{
		switch (operatingSystem)
		{
			case LINUX:
				switch (architecture)
				{
					case X86_64:
						return "linux-x86_64.tar.gz";
					case AARCH_64:
						return "linux-aarch64.tar.gz";
					case ARM_32:
						// cmake is assumed to ship with the platform
					default:
						throw new UnsupportedOperationException("Unsupported platform: " + getName());
				}
			case OSX:
				switch (architecture)
				{
					case X86_64:
					case ARM_32:
					case AARCH_64:
						return "macos-universal.tar.gz";
					default:
						throw new UnsupportedOperationException("Unsupported platform: " + getName());
				}
			case FREEBSD:
			{
				throw new UnsupportedOperationException("Platform requires CMake to be on the PATH.\n" +
					"Platform: " + getName() + "\n" +
					"PATH    : " + System.getenv("PATH"));
			}
			case WINDOWS:
				switch (architecture)
				{
					case X86_64:
						return "windows-x86_64.zip";
					case AARCH_64:
						return "windows-arm64.zip";
					default:
						throw new UnsupportedOperationException("Unsupported platform: " + getName());
				}
			default:
				throw new UnsupportedOperationException("Unsupported platform: " + getName());
		}
	}

	/**
	 * Indicates if the platform supports POSIX attributes.
	 *
	 * @param in the InputStream associated with the archive
	 * @return true if the platform supports POSIX attributes
	 * @throws UnsupportedOperationException if <a href="https://cmake.org/download/">CMake's website</a>
	 *                                       does not provide binaries for this platform
	 */
	public boolean supportsPosix(InputStream in)
	{
		switch (operatingSystem)
		{
			case LINUX:
			case OSX:
			case FREEBSD:
				return in instanceof ArchiveInputStream;
			case WINDOWS:
				return false;
			default:
				throw new UnsupportedOperationException("Unsupported platform: " + getName());
		}
	}

	/**
	 * Returns the value of a {@code ProcessBuilder}'s environment variable.
	 *
	 * @param processBuilder a {@code ProcessBuilder}
	 * @param name           an environment variable
	 * @return the value of the environment variable
	 */
	public String getEnvironment(ProcessBuilder processBuilder, String name)
	{
		Map<String, String> environment = processBuilder.environment();
		return environment.get(operatingSystem.getEnvironmentCanonicalName(environment, name));
	}

	/**
	 * Overrides the environment variables of a process builder.
	 *
	 * @param source new environment variables
	 * @param target existing environment variables
	 * @throws NullPointerException if any of the arguments are null
	 */
	public void overrideEnvironmentVariables(Map<String, String> source, ProcessBuilder target)
	{
		assert (source != null);
		assert (target != null);
		if (source.isEmpty())
			return;

		Map<String, String> environment = target.environment();
		for (Entry<String, String> entry : source.entrySet())
		{
			String value = entry.getValue();
			if (value == null)
			{
				// Maven converts empty properties to null and Linux does not support null values,
				// so we convert them back to empty strings:
				// https://github.com/cmake-maven-plugin/cmake-maven-plugin/issues/11
				value = "";
			}
			String name = entry.getKey();
			name = operatingSystem.getEnvironmentCanonicalName(environment, name);
			environment.put(name, value);
		}
	}

	/**
	 * @param operatingSystem the operating system of the platform
	 * @param architecture    the architecture of the platform
	 * @throws AssertionError if any of the arguments are null
	 */
	Platform(OperatingSystem operatingSystem, Architecture architecture)
	{
		assert (operatingSystem != null) : "operatingSystem may not be null";
		assert (architecture != null) : "architecture may not be null";
		this.operatingSystem = operatingSystem;
		this.architecture = architecture;
	}

	@Override
	public String toString()
	{
		return operatingSystem + " " + architecture;
	}

	/**
	 * The architecture of a platform.
	 * <p>
	 * Naming convention based on <a href="https://github.com/trustin/os-maven-plugin">os-maven-plugin</a>.
	 */
	public enum Architecture
	{
		/**
		 * x86, 32-bit.
		 */
		X86_32,
		/**
		 * x86, 64-bit.
		 */
		X86_64,
		/**
		 * ARM, 32-bit.
		 */
		ARM_32,
		/**
		 * ARM, 64-bit.
		 */
		AARCH_64;

		private static final Reference<Architecture> DETECTED = ConcurrentLazyReference.create(() ->
		{
			String osArch = System.getProperty("os.arch").toLowerCase(Locale.ENGLISH).replaceAll("[^a-z0-9]+", "");
			switch (osArch)
			{
				case "x8632":
				case "x86":
				case "i386":
				case "i486":
				case "i586":
				case "i686":
				case "ia32":
				case "x32":
					return X86_32;
				case "x8664":
				case "amd64":
				case "ia32e":
				case "em64t":
				case "x64":
					return X86_64;
				case "arm":
				case "arm32":
					return ARM_32;
				case "aarch64":
					return AARCH_64;
				default:
					throw new AssertionError("Unsupported architecture: " + osArch);
			}
		});

		/**
		 * Detects and returns the current architecture.
		 *
		 * @return the architecture
		 * @throws AssertionError if no match was found
		 */
		public static Architecture detected()
		{
			return DETECTED.getValue();
		}
	}

	/**
	 * The operating system of a platform.
	 * <p>
	 * Naming convention based on <a href="https://github.com/trustin/os-maven-plugin">os-maven-plugin</a>.
	 */
	public enum OperatingSystem
	{
		/**
		 * Windows.
		 */
		WINDOWS
			{
				@Override
				String getEnvironmentCanonicalName(Map<String, String> environment, String name)
				{
					// WORKAROUND: https://bugs.openjdk.java.net/browse/JDK-8245431
					for (String canonicalName : environment.keySet())
						if (canonicalName.equalsIgnoreCase(name))
							return canonicalName;
					return name;
				}
			},
		/**
		 * Linux.
		 */
		LINUX,
		/**
		 * macOS.
		 */
		OSX,
		/**
		 * FreeBSD.
		 */
		FREEBSD;

		private static final Reference<OperatingSystem> DETECTED = ConcurrentLazyReference.create(() ->
		{
			String name = System.getProperty("os.name");
			if (startsWithIgnoreCase(name, "Windows"))
				return WINDOWS;
			if (startsWithIgnoreCase(name, "Linux"))
				return LINUX;
			if (startsWithIgnoreCase(name, "Mac"))
				return OSX;
			if (startsWithIgnoreCase(name, "FreeBSD"))
				return FREEBSD;
			throw new AssertionError("Unsupported operating system: " + name);
		});

		/**
		 * Detects and returns the current operating system.
		 *
		 * @return the operating system
		 * @throws AssertionError if no match was found
		 */
		public static OperatingSystem detected()
		{
			return DETECTED.getValue();
		}

		/**
		 * @param str    a string
		 * @param prefix a prefix
		 * @return true if {@code start} starts with {@code prefix}, disregarding case sensitivity
		 * @throws NullPointerException if any of the arguments are null
		 */
		private static boolean startsWithIgnoreCase(String str, String prefix)
		{
			return str.regionMatches(true, 0, prefix, 0, prefix.length());
		}

		/**
		 * @param environment a {@code ProcessBuilder}'s environment variables
		 * @param name        an environment variable
		 * @return the case-sensitive form of the variable
		 */
		String getEnvironmentCanonicalName(Map<String, String> environment, String name)
		{
			return name;
		}
	}
}