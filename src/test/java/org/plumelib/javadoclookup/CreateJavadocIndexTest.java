package org.plumelib.javadoclookup;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.checkerframework.checker.modifiability.qual.Modifiable;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * End-to-end tests for {@link CreateJavadocIndex}. Each test runs the program in a subprocess and
 * compares the program's standard output, standard error, and exit status to goal files.
 *
 * <p>Each subdirectory of {@code src/test/resources/testdata} is one test case. The program is run
 * with the test case directory as both its current directory and its home directory, so all the
 * file names in the goal files are relative to the test case directory. See {@code
 * src/test/resources/testdata/README.md} for the layout of a test case directory.
 *
 * <p>By default, the tests run this implementation of CreateJavadocIndex. To run a different
 * implementation, possibly written in a different language, pass the command that runs it: {@code
 * ./gradlew test -Pe2e.command="python3 /path/to/create_javadoc_index.py"}.
 */
final class CreateJavadocIndexTest {

  /** Creates a new CreateJavadocIndexTest. */
  CreateJavadocIndexTest() {}

  /** The name of the file, in a test case directory, that holds the command-line arguments. */
  private static final String ARGS_FILE = "args.txt";

  /** The name of the goal file, in a test case directory, for the program's standard output. */
  private static final String STDOUT_GOAL_FILE = "expected-stdout.txt";

  /** The name of the goal file, in a test case directory, for the program's standard error. */
  private static final String STDERR_GOAL_FILE = "expected-stderr.txt";

  /** The name of the goal file, in a test case directory, for the program's exit status. */
  private static final String STATUS_GOAL_FILE = "expected-status.txt";

  /**
   * The name of the file, in a test case directory, that lists the files and directories that the
   * test makes unreadable before running the program.
   */
  private static final String UNREADABLE_FILE = "unreadable.txt";

  /**
   * The line that replaces a stack trace in the normalized output. In a goal file for standard
   * error, it indicates that the program terminated with an uncaught exception.
   */
  private static final String STACK_TRACE_LINE = "\tat ...";

  /** How long to wait for the program to finish, in seconds. */
  private static final long TIMEOUT_SECONDS = 120;

  /**
   * The environment variables that supply command-line arguments to the java launcher. The test
   * unsets them so that they affect neither the program's behavior nor its output.
   */
  private static final Set<String> JVM_OPTIONS_ENV_VARS =
      Set.of("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS");

  /**
   * If true, overwrite the goal files with the program's current output rather than comparing the
   * two. Set by {@code ./gradlew test -DupdateGoals=true}.
   */
  private static final boolean updateGoals = Boolean.getBoolean("updateGoals");

  /**
   * The words of the command that runs an implementation of CreateJavadocIndex other than this one,
   * or the empty list to run this implementation. Set by {@code ./gradlew test
   * -Pe2e.command="..."}; the value is split at whitespace, and quoting is not supported.
   */
  private static final List<String> externalCommand =
      Arrays.stream(System.getProperty("e2e.command", "").trim().split("\\s+"))
          .filter(word -> !word.isEmpty())
          .toList();

  /**
   * Returns the value of the given system property. {@code build.gradle.kts} sets every system
   * property that these tests require, so they can be run only by Gradle.
   *
   * @param key the name of a system property
   * @return the value of the system property
   * @throws Error if the system property is not set
   */
  private static String requiredProperty(String key) {
    String result = System.getProperty(key);
    if (result == null) {
      throw new Error(
          "System property \""
              + key
              + "\" is not set."
              + "  build.gradle.kts sets it, so run these tests with: ./gradlew test");
    }
    return result;
  }

  /**
   * Returns the directory that contains one subdirectory per test case.
   *
   * @return the directory that contains one subdirectory per test case
   */
  private static Path testdataDir() {
    return Path.of(requiredProperty("projectDir"), "src", "test", "resources", "testdata");
  }

  /**
   * Returns the name of each test case, which is the name of a subdirectory of the test data
   * directory.
   *
   * @return the name of each test case
   * @throws IOException if the test data directory cannot be read
   */
  private static Stream<String> testCases() throws IOException {
    Path testdataDir = testdataDir();
    try (Stream<Path> entries = Files.list(testdataDir)) {
      return entries
          .filter(Files::isDirectory)
          .map(entry -> testdataDir.relativize(entry).toString())
          .sorted()
          .toList()
          .stream();
    }
  }

  /**
   * Runs CreateJavadocIndex on one test case and compares the program's output to the goal files.
   *
   * @param testCase the name of a subdirectory of the test data directory
   * @param tempDir a directory in which to capture the program's output
   * @throws IOException if a file cannot be read or written
   * @throws InterruptedException if waiting for the program is interrupted
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("testCases")
  void endToEnd(String testCase, @TempDir Path tempDir) throws IOException, InterruptedException {
    // The goal files spell file names in the Unix style, with "/" as the separator.
    assumeTrue(File.separatorChar == '/', "The goal files use Unix file name syntax.");

    if (updateGoals && !externalCommand.isEmpty()) {
      throw new Error("Do not overwrite the goal files with the output of another implementation.");
    }

    Path caseDir = testdataDir().resolve(testCase);
    Path stdoutFile = tempDir.resolve("stdout.txt");
    Path stderrFile = tempDir.resolve("stderr.txt");

    // The directory in which the program runs.  It is the test case directory itself, unless the
    // test case makes some files unreadable:  then it is a copy, so that the test never changes
    // the permissions of a file in the source tree.
    Path runDir = caseDir;
    List<Path> unreadable = readUnreadable(caseDir.resolve(UNREADABLE_FILE));
    if (!unreadable.isEmpty()) {
      runDir = tempDir.resolve(testCase);
      copyDirectory(caseDir, runDir);
    }

    int status;
    try {
      for (Path path : unreadable) {
        runDir.resolve(path).toFile().setReadable(false, false);
      }
      for (Path path : unreadable) {
        // A process that runs as the superuser can read any file.
        assumeTrue(
            !Files.isReadable(runDir.resolve(path)),
            "This process can read a file that it has made unreadable: " + path);
      }
      status = runProgram(testCase, runDir, stdoutFile, stderrFile);
    } finally {
      // Make the files readable again, so that the temporary directory can be deleted.
      for (Path path : unreadable) {
        runDir.resolve(path).toFile().setReadable(true, false);
      }
    }

    String stdout = normalize(Files.readString(stdoutFile, UTF_8), runDir);
    String stderr = normalize(Files.readString(stderrFile, UTF_8), runDir);

    // Check all three goal files even if the first differs.  When the program fails unexpectedly,
    // its standard output differs too, and the stack trace on standard error is what explains the
    // failure.
    assertAll(
        () ->
            checkGoal(
                caseDir.resolve(STDOUT_GOAL_FILE), stdout, null, testCase + ": standard output"),
        () -> checkStderrGoal(caseDir.resolve(STDERR_GOAL_FILE), stderr, testCase),
        () ->
            checkGoal(
                caseDir.resolve(STATUS_GOAL_FILE),
                status + "\n",
                "0\n",
                testCase + ": exit status"));
  }

  /**
   * Runs CreateJavadocIndex in a subprocess, with the given directory as its current directory and
   * its home directory.
   *
   * @param testCase the name of a subdirectory of the test data directory
   * @param runDir the directory in which to run the program: the test case directory or a copy of
   *     it
   * @param stdoutFile the file to which to write the program's standard output
   * @param stderrFile the file to which to write the program's standard error
   * @return the program's exit status
   * @throws IOException if the program cannot be started or its arguments cannot be read
   * @throws InterruptedException if waiting for the program is interrupted
   */
  private static int runProgram(String testCase, Path runDir, Path stdoutFile, Path stderrFile)
      throws IOException, InterruptedException {
    List<String> command = new ArrayList<>();
    if (externalCommand.isEmpty()) {
      command.add(Path.of(requiredProperty("java.home"), "bin", "java").toString());
      // With no command-line arguments, CreateJavadocIndex reads ~/.javadoc-index-files, so make
      // the test case directory be the home directory.  Java ignores the HOME environment
      // variable, which is set below for other implementations.
      command.add("-Duser.home=" + runDir);
      // Make the output encoding independent of the encoding of the terminal that runs the tests.
      command.add("-Dfile.encoding=UTF-8");
      command.add("-Dstdout.encoding=UTF-8");
      command.add("-Dstderr.encoding=UTF-8");
      // Measure the coverage of the program, which the JaCoCo agent in the test JVM does not
      // observe because the program runs in a subprocess.  See build.gradle.kts.
      command.add(
          "-javaagent:"
              + requiredProperty("jacocoSubprocessAgentJar")
              + "=destfile="
              + Path.of(requiredProperty("jacocoSubprocessDir"), testCase + ".exec"));
      command.add("-cp");
      command.add(requiredProperty("mainRuntimeClasspath"));
      command.add(CreateJavadocIndex.class.getName());
    } else {
      command.addAll(externalCommand);
    }
    command.addAll(readArgs(runDir.resolve(ARGS_FILE), runDir));

    ProcessBuilder processBuilder =
        new ProcessBuilder(command)
            .directory(runDir.toFile())
            .redirectOutput(stdoutFile.toFile())
            .redirectError(stderrFile.toFile());
    @Modifiable Map<String, String> env = processBuilder.environment();
    env.put("HOME", runDir.toString());
    // The java launcher writes a line such as "Picked up JAVA_TOOL_OPTIONS: ..." to standard
    // error if any of these environment variables is set, which would differ from the goal file.
    // Removing them also prevents them from changing the program's behavior.
    env.keySet().removeAll(JVM_OPTIONS_ENV_VARS);
    Process process = processBuilder.start();
    if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
      process.destroyForcibly();
      throw new AssertionError(
          testCase + ": the program did not finish within " + TIMEOUT_SECONDS + " seconds.");
    }
    return process.exitValue();
  }

  /**
   * Returns the files that a test case makes unreadable: the lines of the given file that are
   * neither blank nor comments. If the file does not exist, returns the empty list.
   *
   * @param unreadableFile a file that lists file names relative to the test case directory, one
   *     per line
   * @return the file names that the file lists
   * @throws IOException if the file cannot be read
   */
  private static List<Path> readUnreadable(Path unreadableFile) throws IOException {
    if (!Files.exists(unreadableFile)) {
      return List.of();
    }
    List<Path> result = new ArrayList<>();
    for (String line : Files.readAllLines(unreadableFile, UTF_8)) {
      String fileName = line.trim();
      if (!fileName.isEmpty() && !fileName.startsWith("#")) {
        result.add(Path.of(fileName));
      }
    }
    return result;
  }

  /**
   * Copies a directory and everything in it. Symbolic links are copied as links.
   *
   * @param source the directory to copy
   * @param target where to create the copy; must not exist
   * @throws IOException if a file cannot be read or written
   */
  private static void copyDirectory(Path source, Path target) throws IOException {
    try (Stream<Path> paths = Files.walk(source)) {
      for (Path path : paths.toList()) {
        Files.copy(
            path,
            target.resolve(source.relativize(path).toString()),
            StandardCopyOption.COPY_ATTRIBUTES,
            LinkOption.NOFOLLOW_LINKS);
      }
    }
  }

  /**
   * Returns the command-line arguments for a test case: the lines of the given file that are
   * neither blank nor comments. In each argument, {@code ${testcase}} is replaced by the test case
   * directory, which lets a test case pass an absolute file name. If the file does not exist,
   * returns the empty list, which makes CreateJavadocIndex read {@code ~/.javadoc-index-files}
   * instead.
   *
   * @param argsFile the file that lists the command-line arguments, one per line
   * @param caseDir the test case directory
   * @return the command-line arguments
   * @throws IOException if the file cannot be read
   * @throws AssertionError if the file exists but lists no argument, which is most likely a typo
   *     rather than a request to run the program with no command-line arguments
   */
  private static List<String> readArgs(Path argsFile, Path caseDir) throws IOException {
    if (!Files.exists(argsFile)) {
      return List.of();
    }
    List<String> result = new ArrayList<>();
    for (String line : Files.readAllLines(argsFile, UTF_8)) {
      String arg = line.trim();
      if (!arg.isEmpty() && !arg.startsWith("#")) {
        result.add(arg.replace("${testcase}", caseDir.toString()));
      }
    }
    // Running the program with no command-line arguments makes it read ".javadoc-index-files",
    // which is not what a file that lists no argument asks for.
    if (result.isEmpty()) {
      throw new AssertionError(
          "File "
              + argsFile
              + " lists no command-line argument."
              + "  To run the program with no command-line arguments, delete the file.");
    }
    return result;
  }

  /**
   * Makes the program's output independent of where the repository is checked out and of the line
   * numbers in stack traces. The result still depends on the behavior of the program and of its
   * dependencies; for example, some goal files contain text that jsoup produced, so upgrading jsoup
   * may require the goal files to be updated. That is intended: a goal file changes exactly when
   * the program's user-visible output changes.
   *
   * @param output the program's standard output or standard error
   * @param caseDir the directory that was the program's current directory: the test case
   *     directory or a copy of it
   * @return the output, with machine-specific text replaced
   */
  private static String normalize(String output, Path caseDir) {
    // CreateJavadocIndex prints an absolute file name only when it constructs one from the home
    // directory, which this test set to the test case directory.
    String result = output.replace(caseDir.toString(), "${testcase}");
    // Replace each stack trace by a single line, because a stack trace contains line numbers
    // that change whenever CreateJavadocIndex or one of its dependencies is edited.
    result = result.replaceAll("(?m)^\tat .*(\r?\n\tat .*)*", STACK_TRACE_LINE);
    return result;
  }

  /**
   * Compares the program's standard error to its goal file, like {@link #checkGoal}. However, when
   * the tests run an implementation other than this one and the goal file records an uncaught Java
   * exception, the text of that exception is specific to Java, so the comparison requires only that
   * standard error is not empty.
   *
   * @param goalFile the goal file for standard error
   * @param actual the program's standard error
   * @param testCase the name of the test case; used for diagnostics
   * @throws IOException if the goal file cannot be read or written
   */
  private static void checkStderrGoal(Path goalFile, String actual, String testCase)
      throws IOException {
    String description = testCase + ": standard error";
    if (!externalCommand.isEmpty()
        && Files.exists(goalFile)
        && Files.readAllLines(goalFile, UTF_8).contains(STACK_TRACE_LINE)) {
      assertFalse(
          actual.isEmpty(),
          description + " is empty, but goal file " + goalFile + " records an uncaught exception");
      return;
    }
    checkGoal(goalFile, actual, "", description);
  }

  /**
   * Compares the program's output to a goal file, or overwrites the goal file if {@link
   * #updateGoals} is true.
   *
   * @param goalFile the goal file
   * @param actual the program's output
   * @param defaultGoal the expected output if the goal file does not exist, or null if the goal
   *     file is required to exist
   * @param description identifies the test case and the output being compared
   * @throws IOException if the goal file cannot be read or written
   */
  private static void checkGoal(
      Path goalFile, String actual, @Nullable String defaultGoal, String description)
      throws IOException {
    if (updateGoals) {
      if (actual.equals(defaultGoal)) {
        Files.deleteIfExists(goalFile);
      } else {
        Files.writeString(goalFile, actual, UTF_8);
      }
      return;
    }

    String expected;
    if (Files.exists(goalFile)) {
      expected = Files.readString(goalFile, UTF_8);
    } else if (defaultGoal != null) {
      expected = defaultGoal;
    } else {
      throw new AssertionError(
          "Goal file "
              + goalFile
              + " does not exist."
              + "  Create it by running: ./gradlew test -DupdateGoals=true");
    }
    assertEquals(expected, actual, description + " (goal file " + goalFile + ")");
  }
}
