# CMake-Maven-Plugin [![build-status](../../workflows/Build/badge.svg)](../../actions?query=workflow%3ABuild)

## Introduction

A Maven project for the CMake build system. It can be used by including it as a plugin within your Maven
project's pom.xml file.

This repository [originally lived](https://code.google.com/p/cmake-maven-project/) on Google Code and was
migrated to GitHub (and Git) after Google Code shut down.

## Sample Usage

### Generate Goal

```xml

<plugin>
  <groupId>io.github.cmake-maven-plugin</groupId>
  <artifactId>cmake-maven-plugin</artifactId>
  <version>3.31.5-b2</version>
  <executions>
    <execution>
      <id>cmake-generate</id>
      <goals>
        <goal>generate</goal>
      </goals>
      <configuration>
        <sourcePath>
          <!-- The directory containing CMakeLists -->
        </sourcePath>
        <targetPath>
          <!-- The directory to write the project files to -->
        </targetPath>
        <generator>
          <!--
          Optional: Overrides the default generator used by cmake.
          The list of available values can be found at 
          https://cmake.org/cmake/help/v3.22/manual/cmake-generators.7.html
          -->
        </generator>
        <environmentVariables>
          <!--
          Optional: Additional environment variables to expose to cmake. If a variable was already set,
          overrides the previous value.             
          -->
          <key>value</key>
        </environmentVariables>
        <options>
          <!--
          Optional: One or more options found at https://cmake.org/cmake/help/v3.22/manual/cmake.1.html
          For example:
          -->
          <option>-DBUILD_THIRDPARTY:bool=on</option>
        </options>
      </configuration>
    </execution>
  </executions>
</plugin>
```

### Compile Goal

```xml

<plugin>
  <groupId>io.github.cmake-maven-plugin</groupId>
  <artifactId>cmake-maven-plugin</artifactId>
  <version>3.31.5-b2</version>
  <executions>
    <execution>
      <id>cmake-compile</id>
      <goals>
        <goal>compile</goal>
      </goals>
      <configuration>
        <config>
          <!-- Optional: the build configuration (e.g. "x64|Release") -->
        </config>
        <target>
          <!-- Optional: the build "target" -->
        </target>
        <projectDirectory>
          <!-- "targetPath" from the "generate" goal -->
        </projectDirectory>
        <environmentVariables>
          <key>value</key>
        </environmentVariables>
      </configuration>
    </execution>
  </executions>
</plugin>
```

### Test Goal

```xml

<plugin>
  <groupId>io.github.cmake-maven-plugin</groupId>
  <artifactId>cmake-maven-plugin</artifactId>
  <version>3.31.5-b2</version>
  <executions>
    <execution>
      <id>cmake-test</id>
      <goals>
        <goal>test</goal>
      </goals>
      <configuration>
        <!-- "buildDirectory" is "targetPath" from the "generate" goal -->
        <buildDirectory>${project.build.directory}</buildDirectory>
        <!-- Optional: do not fail the build on test failures. false by default. -->
        <ignoreTestFailure>true</ignoreTestFailure>
        <!-- Optional: skip only ctest tests. false by default. -->
        <ctest.skip.tests>true</ctest.skip.tests>
        <!-- Optional: Skip all Maven tests. false by default -->
        <maven.test.skip>true</maven.test.skip>
        <!-- Optional: the number of threads tests should use -->
        <threadCount>2</threadCount>
        <!-- Optional: dashboard configuration; used with CTestConfig.cmake -->
        <dashboard>Experimental</dashboard>
      </configuration>
    </execution>
  </executions>
</plugin>
```

### Install Goal

```xml

<plugin>
  <groupId>io.github.cmake-maven-plugin</groupId>
  <artifactId>cmake-maven-plugin</artifactId>
  <version>3.31.5-b2</version>
  <executions>
    <execution>
      <id>cmake-install</id>
      <goals>
        <goal>install</goal>
      </goals>
      <configuration>
        <config>
          <!-- Optional: the build configuration (e.g. "x64|Release") -->
        </config>
        <projectDirectory>
          <!-- "targetPath" from the "generate" goal -->
        </projectDirectory>
        <prefix>
          <!-- Optional: path prefix to the installation destination (e.g. "${user.home}/.local", or "/usr/local") -->
        </prefix>
        <environmentVariables>
          <key>value</key>
        </environmentVariables>
      </configuration>
    </execution>
  </executions>
</plugin>
```

### Examples

The following projects contain examples of how to use this plugin:

[Requirements API](https://github.com/cowwoc/requirements.java/blob/ed0fb648947284ddb1a28959bf8003c0807e3bef/natives/pom.xml#L69)

### Building instructions

To build the plugin, run:

    mvn install

To clean an old build, run:

    mvn clean

By default, Maven will activate the right profile based on your JVM:

* windows-x86_64
* windows-aarch_64
* linux-x86_64
* linux-arm_32
* linux-aarch_64
* mac-x86_64
* mac-aarch_64

If detection does not work, or you wish to override it then set `-Dos.name=<value>` and `-Dos.arch=<value>`.

For instance, when building for 64-bit Linux machines, use:

    mvn -Dos.name="linux" -Dos.arch="x86_64" install

You may need to set up a `~/.m2/toolchain.xml` file to refer to your Java8-compatible installation location.

### Using a local CMake installation

Sometimes it is preferable or necessary to use a preexisting CMake installation. cmake.org doesn't provide
binaries for some platforms, such as Raspberry Pi. In such cases, users can install the binaries themselves
(typically using package managers like `apt-get`) and point the plugin at them.

1. Set `${cmake.download}` to `false`. This property defaults to `true` on platforms that are supported
   by [CMake downloads](https://cmake.org/download/) and `false` otherwise.
2. Optionally set `${cmake.dir}` to the directory containing the binaries (e.g. `/usr/bin`). Otherwise, the
   plugin will expect the binaries to be on the PATH.

That's it! To learn more about CMake itself, consult the [CMake.org](https://cmake.org/) website.

### License

* This library is distributed under the terms of
  the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)
* See [Third party licenses](LICENSE-3RD-PARTY.md) for the licenses of dependencies
