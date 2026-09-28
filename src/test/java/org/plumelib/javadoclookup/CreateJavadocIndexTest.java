package org.plumelib.javadoclookup;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
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

    Path caseDir = testdataDir().resolve(testCase);
    Path stdoutFile = tempDir.resolve("stdout.txt");
    Path stderrFile = tempDir.resolve("stderr.txt");

    List<String> command = new ArrayList<>();
    command.add(Path.of(requiredProperty("java.home"), "bin", "java").toString());
    // With no command-line arguments, CreateJavadocIndex reads ~/.javadoc-index-files, so make
    // the test case directory be the home directory.
    command.add("-Duser.home=" + caseDir);
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
    command.addAll(readArgs(caseDir.resolve(ARGS_FILE), caseDir));

    ProcessBuilder processBuilder =
        new ProcessBuilder(command)
            .directory(caseDir.toFile())
            .redirectOutput(stdoutFile.toFile())
            .redirectError(stderrFile.toFile());
    // The java launcher writes a line such as "Picked up JAVA_TOOL_OPTIONS: ..." to standard
    // error if any of these environment variables is set, which would differ from the goal file.
    // Removing them also prevents them from changing the program's behavior.
    processBuilder.environment().keySet().removeAll(JVM_OPTIONS_ENV_VARS);
    Process process = processBuilder.start();
    if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
      process.destroyForcibly();
      throw new AssertionError(
          testCase + ": the program did not finish within " + TIMEOUT_SECONDS + " seconds.");
    }
    int status = process.exitValue();

    String stdout = normalize(Files.readString(stdoutFile, UTF_8), caseDir);
    String stderr = normalize(Files.readString(stderrFile, UTF_8), caseDir);

    // Check all three goal files even if the first differs.  When the program fails unexpectedly,
    // its standard output differs too, and the stack trace on standard error is what explains the
    // failure.
    assertAll(
        () ->
            checkGoal(
                caseDir.resolve(STDOUT_GOAL_FILE), stdout, null, testCase + ": standard output"),
        () ->
            checkGoal(caseDir.resolve(STDERR_GOAL_FILE), stderr, "", testCase + ": standard error"),
        () ->
            checkGoal(
                caseDir.resolve(STATUS_GOAL_FILE),
                status + "\n",
                "0\n",
                testCase + ": exit status"));
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
   * @param caseDir the test case directory, which was the program's current directory
   * @return the output, with machine-specific text replaced
   */
  private static String normalize(String output, Path caseDir) {
    // CreateJavadocIndex prints an absolute file name only when it constructs one from the home
    // directory, which this test set to the test case directory.
    String result = output.replace(caseDir.toString(), "${testcase}");
    // Replace each stack trace by a single line, because a stack trace contains line numbers
    // that change whenever CreateJavadocIndex or one of its dependencies is edited.
    result = result.replaceAll("(?m)^\tat .*(\r?\n\tat .*)*", "\tat ...");
    return result;
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
