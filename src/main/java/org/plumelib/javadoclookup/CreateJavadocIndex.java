package org.plumelib.javadoclookup;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.checkerframework.checker.nullness.qual.KeyFor;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

// Handles only globs within a single directory.

/**
 * CreateJavadocIndex reads {@code index-all.html} API documentation files (typically {@code
 * index-all.html} files) and outputs an index that Emacs can use for looking up Java documentation.
 *
 * <p>With no arguments, it reads file {@code ~/.javadoc-index-files}, which should contain a list
 * of API documentation files, one per line. Blank lines are permitted in the file, as are comment
 * lines that start with "#".
 *
 * <p>For example, run it like this:
 *
 * <pre>
 * java -cp .../javadoc-lookup/build/libs/javadoc-lookup-all.jar \
 *   org.plumelib.javadoclookup.CreateJavadocIndex &gt; ~/.javadoc-index.el
 * </pre>
 */
public final class CreateJavadocIndex {

  /** If true, output diagnostic logging. */
  private static final boolean debug = false;

  /** The index from symbols to Javadoc URLs. */
  private static Map<String, Set<String>> index = new HashMap<>();

  /** The ignored prefixes (base directories for Javadoc files). */
  private static Set<String> ignoredPrefixes = new TreeSet<>();

  /** This class is a collection of methods; it does not represent anything. */
  private CreateJavadocIndex() {
    throw new Error("do not instantiate");
  }

  /**
   * Scan the specified file(s) for API documentation index and print the corresponding entries.
   *
   * @param args command-line arguments: {@code index-all.html} files
   * @throws IOException if there is a problem reading a file
   */
  public static void main(String[] args) throws IOException {

    // If no arguments supplied, use the contents of file ~/.javadoc-index-files .
    List<String> indexFileNames;
    if (args.length != 0) {
      indexFileNames = Arrays.asList(args);
    } else {
      String javadocIndexFilesFile = System.getProperty("user.home") + "/" + ".javadoc-index-files";
      indexFileNames = readAndGlobFiles(javadocIndexFilesFile);
    }

    for (String indexFileName : indexFileNames) {
      if (debug) {
        System.out.println("About to parse: " + indexFileName);
      }
      File indexFile = new File(indexFileName);
      Document doc = Jsoup.parse(indexFile, "UTF-8");
      Path dir = indexFile.toPath().getParent();
      if (dir == null) {
        System.err.println("Null dir for " + indexFile);
        System.exit(1);
      }

      addIgnoredPrefix(indexFile, dir);

      Elements classElts = doc.select("span[class=memberNameLink]");
      for (Element classElt : classElts) {
        Element ahref = classElt.selectFirst("a[href]");
        if (ahref == null) {
          System.err.println("In " + indexFileName + ", no <a href=...> in: " + classElt);
          System.err.println("parent = " + classElt.parent());
          System.err.println("CreateJavadocIndex FAILED; exiting.");
          System.exit(1);
        }
        addToIndex(ahref.html(), ahref.attributes().get("href"), dir);
      }

      Elements mnlAnchors = doc.select("a[class=member-name-link]");
      for (Element mnlAnchor : mnlAnchors) {
        addToIndex(mnlAnchor.html(), mnlAnchor.attributes().get("href"), dir);
      }

      Elements atitleElts = doc.select("a[title]");
      for (Element atitle : atitleElts) {
        String title = atitle.attributes().get("title");
        if (debug) {
          System.out.println("atitle = " + atitle);
          System.out.println("  title = " + title);
        }
        if (title.startsWith("annotation in ")
            || title.startsWith("annotation interface in ")
            || title.startsWith("class in ")
            || title.startsWith("class or interface in ") // do I want this one?
            || title.startsWith("enum in ")
            || title.startsWith("enum class in ")
            || title.startsWith("interface in ")
        // I don't want type parameters.
        // || title.startsWith("type parameter in ")
        ) {
          addToIndex(atitle.html(), atitle.attributes().get("href"), dir);
        }
      }
    }

    System.out.println(";; For use by Emacs function javadoc-lookup.");
    System.out.println(";; Created by CreateJavadocIndex.");
    System.out.println(";; arguments: " + String.join(" ", indexFileNames));
    System.out.println("(setq javadoc-html-refs '(");

    // TODO: Under JDK 18, `@NonNull` is required here but no warning suppression is required.
    @NonNull List<@KeyFor("index") String> sortedKeys =
        index.keySet().stream().sorted(Comparator.reverseOrder()).collect(Collectors.toList());
    for (String key : sortedKeys) {
      System.out.print(" (\"" + key.replace("\"", "\\\"") + "\"");
      for (String ref : index.get(key)) {
        System.out.print(" \"" + ref + "\"");
      }
      System.out.println(")");
    }
    System.out.println("))");

    System.out.println();
    System.out.println("(setq javadoc-ignored-prefixes (list");
    for (String prefix : ignoredPrefixes) {
      System.out.println("  (concat \"^\" (regexp-quote \"" + prefix + File.separator + "\"))");
    }
    System.out.println("))");
  }

  /**
   * Augment the ignoredPrefixes data structure.
   *
   * @param input the index file
   * @param dir the directory in which the index file is found
   */
  private static void addIgnoredPrefix(File input, Path dir) {
    Path ignoredPrefix = dir;
    Path ignoredName = ignoredPrefix.getFileName();
    if (ignoredName == null) {
      System.err.println("Null parent dir for " + input);
      System.exit(1);
    }
    if (ignoredName.toString().equals("index-files")) {
      ignoredPrefix = ignoredPrefix.getParent();
      if (ignoredPrefix == null) {
        System.err.println("Null parent dir for " + input);
        System.exit(1);
      }
    }
    // The API documentation for Jgit is within a "org.eclipse.jgit" subdirectory.
    Path orgEclipseJgitSubdirectory = ignoredPrefix.resolve("org.eclipse.jgit");
    if (orgEclipseJgitSubdirectory.toFile().exists()
        && orgEclipseJgitSubdirectory.toFile().isDirectory()) {
      ignoredPrefix = orgEclipseJgitSubdirectory;
    }
    if (Files.exists(Paths.get(ignoredPrefix.toString(), "java.base"))) {
      // It's the JDK.  Handle modules.
      // TODO: it would be better to compare paths to packages, without special-casing
      try (DirectoryStream<Path> stream = Files.newDirectoryStream(ignoredPrefix)) {
        for (Path file : stream) {
          if (Files.isDirectory(file)) {
            @SuppressWarnings("nullness:dereference.of.nullable") // iterating, cannot be null
            String name = file.getFileName().toString();
            if (name.startsWith("java.") || name.startsWith("jdk.")) {
              ignoredPrefixes.add("file:" + file);
            }
          }
        }
      } catch (IOException | DirectoryIteratorException e) {
        throw new Error(e);
      }
    } else {
      // It's not the JDK.
      ignoredPrefixes.add("file:" + ignoredPrefix);
    }
  }

  /**
   * Adds an entry from the given item/key to its reference.
   *
   * @param item the key, a Java symbol
   * @param href the value, a Java API HTML file URL
   * @param dir the relative directory for the value
   */
  private static void addToIndex(String item, String href, Path dir) {
    if (href.contains("http:") || href.contains("https:")) {
      return;
    }

    item = item.replaceAll("&lt;", "<");
    item = item.replaceAll("&gt;", ">");
    item = item.replaceAll("</?code>", "");
    item = item.replaceAll("<span class=\"[^\"]*\">", "");
    item = item.replaceAll("</span>", "");
    item = item.replaceAll("@[a-zA-Z.]+ ", "");
    item = item.replaceAll("^@", "");

    String fileHref = "file:" + Paths.get(dir.toString(), href).normalize();
    fileHref = fileHref.replaceAll("\\(", "-");
    fileHref = fileHref.replaceAll("\\)", "-");
    fileHref = fileHref.replaceAll("@[a-zA-Z.]+ ", "");
    if (debug) {
      System.out.println("    fileHref: " + fileHref);
    }

    Set<String> hrefs = index.computeIfAbsent(item, key -> new TreeSet<>());
    hrefs.add(fileHref);
  }

  /**
   * Read lines from the given file, each of which is a comment or a filename, possibly including a
   * "*" glob in the file name. Globs in directory names are not handled.
   *
   * @param filename the file that contains possibly-globbed filenames
   * @return the filenames in the given file, with globs expanded
   */
  @SuppressWarnings("PMD.ExceptionAsFlowControl")
  private static List<String> readAndGlobFiles(String filename) {

    try (BufferedReader br = Files.newBufferedReader(Paths.get(filename), UTF_8)) {

      List<String> result = new ArrayList<>();

      for (String line_orig = br.readLine(); line_orig != null; line_orig = br.readLine()) {
        if (debug) {
          System.out.println("readAndGlobFiles: line = " + line_orig);
        }
        String line = line_orig.trim();
        if (line.isEmpty() || line.startsWith("#")) {
          continue;
        }

        int asteriskPos = line.indexOf('*');
        if (debug) {
          System.out.println("asteriskPos = " + asteriskPos);
        }
        if (asteriskPos == -1) {
          // This line is not a glob, but the file might not exist.
          if (Files.exists(Paths.get(line))) {
            result.add(line);
          } else {
            System.err.println("Didn't find " + line);
          }
        } else {
          // This line is a glob
          int slashPos = line.lastIndexOf('/', asteriskPos);
          if (slashPos == -1) {
            System.err.println("glob pattern contains no directory slash");
            System.exit(-1);
          }
          String globDirName = line.substring(0, slashPos);
          Path globDirPath = Paths.get(globDirName);
          String globFileName = line.substring(slashPos + 1);
          if (debug) {
            System.out.printf(
                "slashPos = %d; newDirectoryStream(%s, %s)%n", slashPos, globDirName, globFileName);
          }
          if (Files.exists(globDirPath)) {
            try (DirectoryStream<Path> stream =
                Files.newDirectoryStream(Paths.get(globDirName), globFileName)) {
              for (Path entry : stream) {
                result.add(entry.toString());
              }
            } catch (DirectoryIteratorException ex) {
              // I/O error encounted during the iteration, the cause is an IOException
              throw ex.getCause();
            }
          } else {
            System.err.println("Didn't find " + globDirName);
          }
        }
      }

      //  Sorting makes the results more deterministic, easier to compare.
      Collections.sort(result);
      return result;

    } catch (FileNotFoundException e) {
      System.err.println("File not found: " + filename);
      System.exit(1);
      return new ArrayList<>(); // dead code, but Java compiler requires it
    } catch (IOException e) {
      System.err.println("Trouble while reading file " + filename + " : " + e.getMessage());
      System.exit(1);
      throw new Error(); // dead code, but the Java compiler requires it
    }
  }
}
