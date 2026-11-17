# End-to-end test data for CreateJavadocIndex

Each subdirectory of this directory is one end-to-end test case for
`CreateJavadocIndex`.  `CreateJavadocIndexTest` runs the program once per
subdirectory, in a subprocess, and compares the program's output to goal files.

The test cases are data, not code, so they can be used to test another
implementation of `CreateJavadocIndex`, possibly written in a different language.
This file specifies the format of a test case precisely enough for someone to write
a test harness in another language.

## How the program is run

For each test case directory, the harness runs the program once, as follows:

* The current working directory is the test case directory.
* The home directory is the test case directory.  The harness sets the environment
  variable `HOME` to the absolute file name of the test case directory.  For the
  Java implementation, which ignores `HOME`, the harness also passes
  `-Duser.home=` followed by that file name to the JVM.  Therefore, when the
  program is run with no command-line arguments, it reads the file
  `.javadoc-index-files` in the test case directory.
* The command-line arguments are the ones that `args.txt` lists (see below), in
  order.  If there is no `args.txt` file, the program is run with no command-line
  arguments.
* The harness writes nothing to standard input.
* Standard output and standard error are captured separately, and decoded as
  UTF-8.  The Java implementation is passed `-Dfile.encoding=UTF-8`,
  `-Dstdout.encoding=UTF-8`, and `-Dstderr.encoding=UTF-8`, so that its output
  does not depend on the locale.
* The environment variables `JAVA_TOOL_OPTIONS`, `JDK_JAVA_OPTIONS`, and
  `_JAVA_OPTIONS` are removed, because they would make the java launcher write a
  message to standard error.  The rest of the environment is inherited.
* The program is given 120 seconds to finish.

Because the current directory and the home directory are both the test case
directory, every relative file name in a test case -- in `args.txt`, in
`.javadoc-index-files`, and in the goal files -- is relative to the test case
directory.

The goal files spell file names in the Unix style, with `/` as the separator, so
the Java harness skips the tests on Windows.

## Files in a test case directory

<!-- markdownlint-disable MD013 -->

| File | Meaning |
| --- | --- |
| `args.txt` | The command-line arguments, one per line.  Blank lines and lines that start with `#` are ignored.  In an argument, `${testcase}` is replaced by the absolute file name of the directory in which the program runs (the test case directory, or its copy as described under "Unreadable files"), which lets a test case pass an absolute file name.  Each line is trimmed of leading and trailing whitespace, and each line is one argument even if it contains spaces.  The file must list at least one argument; to run the program with no command-line arguments, omit the file, which makes the program read `.javadoc-index-files`. |
| `.javadoc-index-files` | The list of API documentation files that the program reads when it is given no command-line arguments. |
| `expected-stdout.txt` | Goal file for the program's standard output.  Required. |
| `expected-stderr.txt` | Goal file for the program's standard error.  If neither this file nor `expected-stderr-substrings.txt` exists, the program must write nothing to standard error. |
| `expected-stderr-substrings.txt` | Goal file that specifies only part of the program's standard error; see "Partially specified standard error" below.  A test case must not have both this file and `expected-stderr.txt`. |
| `expected-status.txt` | Goal file for the program's exit status, as a decimal number followed by a newline.  If absent, the program must exit with status 0. |
| `unreadable.txt` | Files and directories that the harness makes unreadable before running the program, one per line, relative to the test case directory.  Blank lines and lines that start with `#` are ignored.  See below. |

All other files in a test case directory are input: HTML API documentation files,
and stub files that exist only so that git records a directory whose mere existence
affects the program's behavior.  Some input files are deliberately not valid UTF-8
or have unusual line endings; a harness must not modify them.

The comparison of standard output and standard error to `expected-stdout.txt` and
`expected-stderr.txt` is exact, after the normalization that is described below:  it
is sensitive to whitespace, to line endings, and to a final newline.  The exit status
is compared as a number.

### Partially specified standard error

When some of the text of a diagnostic legitimately differs from one implementation
to another, a test case specifies only the part that every implementation must
produce.  Such a test case has a file `expected-stderr-substrings.txt` instead of
`expected-stderr.txt`.  Blank lines and lines that start with `#` in the file are
ignored, and each other line is trimmed of leading and trailing whitespace.  Each
remaining line is a string that the program's standard error must contain, after
normalization.  In addition, standard error must not be empty, even if the file
lists no string.  The strings are checked independently:  they can appear in any
order, on the same line or on different lines.

For example, when an input file is unreadable, the reference implementation
(written in Java) terminates with an uncaught exception, which it reports with a
stack trace; another implementation would report the problem differently.  So test
case `unreadable-argument` requires only that standard error is non-empty and
mentions the file `api/index-all.html`, and that standard output and the exit
status are exactly as their goal files specify.  Similarly, test case
`filelist-unreadable` requires the program's own message `Trouble while reading
file ${testcase}/.javadoc-index-files`, but not the text that the reference
implementation appends to it, which is the message of a Java exception.

### Unreadable files

Git does not record whether a file is readable, so a test case that needs an
unreadable file lists it in `unreadable.txt`.  For such a test case, the harness
copies the test case directory to a temporary directory, removes read permission
from each listed file or directory in the copy, and runs the program in the copy
(so the copy, not the original, is the current directory and the home directory).
Afterward, it restores read permission so that the copy can be deleted.  If the
harness can still read a file after removing its read permission, as when it runs
as the superuser, then the harness skips the test case.

<!-- markdownlint-enable MD013 -->

## Normalization of the program's output

Before comparing the program's standard output and standard error to the goal
files, the harness replaces each occurrence of the absolute file name of the
directory in which the program ran (the test case directory, or its copy) by
`${testcase}`, so that the goal files do not depend on where the repository is
checked out.

### Behavior that reflects the reference implementation's HTML library

The goal files record the behavior of the reference implementation, which is
written in Java and parses HTML with the jsoup library.  Diagnostics whose text
depends on the implementation are only partially specified, as described above.
Standard output, however, is always specified exactly, and some of it reflects
jsoup:

* HTML parsing is lenient:  it accepts malformed HTML and non-HTML text, and it
  decodes input as described for test cases `charset-iso-8859-1`,
  `utf8-byte-order-mark`, `utf16-byte-order-mark`, and `invalid-utf8`.
* In the test cases that record known problems with stripping markup from a symbol
  (see the table at the end of this file), the markup that remains in a symbol is
  written as jsoup serializes it.

## Running the tests against another implementation

Run

```shell
./gradlew test -Pe2e.command="COMMAND"
```

where `COMMAND` is the command that runs the other implementation, such as
`python3 /path/to/create_javadoc_index.py`.  The harness splits `COMMAND` at
whitespace (it does not support quoting), appends the arguments that `args.txt`
lists, and runs the result as described above.  The other implementation reads the
home directory from the environment variable `HOME`.  When `-Pe2e.command` is
given, the harness refuses to update the goal files.

## Updating the goal files

Run

```shell
./gradlew test -DupdateGoals=true
```

to overwrite the goal files with the program's current output.  Always inspect the
resulting diff before committing it: a change in the program's output is exactly
what these tests exist to detect.

A file `expected-stderr-substrings.txt` cannot be derived from the program's output,
so it is never overwritten; it is checked as usual, and if it fails, edit it by
hand.  When a new test case's standard error contains text that depends on the
implementation, such as a stack trace, replace the generated `expected-stderr.txt`
by an `expected-stderr-substrings.txt` file.

## What the test cases cover

* Real javadoc output: `javadoc8`, `javadoc17`, `javadoc21`, `javadoc26`, and the
  `-splitindex` variants `javadoc8-split` and `javadoc21-split`.
* The three ways that CreateJavadocIndex recognizes an index entry:
  `member-name-link-span` (javadoc 8), `member-name-link` (javadoc 9 and later),
  `title-kinds` (a link that has a `title` attribute), and
  `class-attribute-exact-match` (which class attribute values are recognized).
* The text of a symbol: `annotation-in-symbol`, `at-sign-in-symbol`,
  `code-element-in-symbol`, `entity-in-symbol`, `html-markup-in-symbol`,
  `non-ascii-symbol`, `other-markup-in-symbol`, `quote-in-symbol`,
  `span-element-in-symbol`, `whitespace-in-symbol`.
* The target of a reference: `at-sign-in-href`, `href-external`,
  `href-http-not-at-start`, `href-missing`, `href-scheme`, `quote-in-href`,
  `relative-href`.
* The ignored prefixes: `jdk-modules`, `jdk-modules-split`, `jgit`, `jgit-split`,
  `index-files-at-root`, `arg-dot-slash`, `arg-absolute`,
  `java-base-is-file`, `jgit-subdirectory-is-file`.
* Command-line arguments: `arg-directory`, `arg-duplicate`, `arg-option-like`,
  `arg-space-in-file-name`, `no-directory-in-argument`, `nonexistent-argument`,
  `unreadable-argument`, `two-libraries`.
* The `.javadoc-index-files` file, including globbing: the `filelist-*` test cases.
  Among them, `filelist-crlf`, `filelist-byte-order-mark`, `filelist-tilde`, and
  `filelist-trailing-comment` concern the syntax of the file;
  `filelist-unreadable`, `filelist-unreadable-entry`, and
  `filelist-glob-directory-unreadable` concern unreadable files.
* Character encodings: `charset-iso-8859-1`, `utf8-byte-order-mark`,
  `utf16-byte-order-mark`, `invalid-utf8`.
* Degenerate input: `empty-file`, `empty-index`, `malformed-html`, `not-html`,
  `missing-anchor`, `duplicate-entries`, `anchor-matched-by-two-rules`,
  `span-with-two-anchors`.
* The form of the output: `key-sorting`.

## The goal files record the current behavior, not the desired behavior

A goal file was created by running the program, so it records what the program does
today.  In several test cases that is not what the program ought to do.  Each such
test case has a comment in its input that says so.  The known problems are:

<!-- markdownlint-disable MD013 -->

| Test case | Problem | Issue |
| --- | --- | --- |
| `entity-in-symbol` | Only the entities `&lt;` and `&gt;` are decoded, so a symbol can contain the literal text `&amp;` or `&nbsp;`. | [#433](https://github.com/plume-lib/javadoc-lookup/issues/433) |
| `code-element-in-symbol` | A `<code>` start tag that has an attribute is not stripped. | [#434](https://github.com/plume-lib/javadoc-lookup/issues/434) |
| `span-element-in-symbol` | A `<span>` start tag that lacks a `class` attribute is not stripped, although the matching `</span>` end tag is. | [#435](https://github.com/plume-lib/javadoc-lookup/issues/435) |
| `other-markup-in-symbol` | Markup other than `code` and `span` is not stripped. | [#436](https://github.com/plume-lib/javadoc-lookup/issues/436) |
| `charset-iso-8859-1` | The program passes `"UTF-8"` to Jsoup, which makes Jsoup ignore the charset that the document declares. | [#437](https://github.com/plume-lib/javadoc-lookup/issues/437) |
| `href-scheme` | The test for an external reference is case-sensitive and knows only `http:` and `https:`, so `HTTPS:`, `//`, `ftp:`, `mailto:`, and `file:` yield a nonexistent local file name. | [#438](https://github.com/plume-lib/javadoc-lookup/issues/438) |
| `href-missing` | An `<a>` element with no `href` attribute yields a reference to the directory that contains the index file, rather than a diagnostic. | [#439](https://github.com/plume-lib/javadoc-lookup/issues/439) |
| `arg-dot-slash` | The ignored prefix is not normalized, so for the argument `./api/index-all.html` the ignored prefix `file:./api/` matches none of the references, which are under `file:api/`. | [#440](https://github.com/plume-lib/javadoc-lookup/issues/440) |
| `at-sign-in-symbol` | Stripping an annotation that precedes `[]` leaves the space that preceded the annotation, and a symbol that is a lone `@` becomes the empty string. | [#441](https://github.com/plume-lib/javadoc-lookup/issues/441) |
| `at-sign-in-href` | The annotation-stripping substitution is applied to the reference as well as to the symbol, so a file name that contains `@`, letters, and a space yields a reference to a file that does not exist. | [#442](https://github.com/plume-lib/javadoc-lookup/issues/442) |
| `filelist-glob-no-match` | A glob that matches no file is silently ignored. | [#443](https://github.com/plume-lib/javadoc-lookup/issues/443) |
| `filelist-glob-in-directory-name` | A glob in a directory name is silently ignored. | [#444](https://github.com/plume-lib/javadoc-lookup/issues/444) |
| `filelist-glob-metacharacters` | A line is treated as a glob only if it contains `*`, so `?`, `[...]`, and `{...}` are not expanded even though `Files.newDirectoryStream` supports them. | [#445](https://github.com/plume-lib/javadoc-lookup/issues/445) |
| `arg-directory`, `filelist-glob-matches-directory`, `unreadable-argument`, `filelist-unreadable-entry` | Naming a directory or an unreadable file, rather than a readable file, produces an uncaught exception and a stack trace, and discards the entries that were read from the files that were processed first. | [#446](https://github.com/plume-lib/javadoc-lookup/issues/446) |
| `href-http-not-at-start` | The test for an external reference looks for `http:` and `https:` anywhere in the href, so a relative reference that contains either one, as in `p/Widget.html#fetch(https:)`, is not indexed. | [#447](https://github.com/plume-lib/javadoc-lookup/issues/447) |
| `filelist-byte-order-mark` | A byte order mark at the start of `.javadoc-index-files` is not stripped, so it becomes part of the first file name, and that file is not found. | [#448](https://github.com/plume-lib/javadoc-lookup/issues/448) |
| `java-base-is-file` | A directory is treated as the JDK's API documentation if it contains an entry named `java.base`, even if that entry is a file; then the directory itself is not an ignored prefix. | [#449](https://github.com/plume-lib/javadoc-lookup/issues/449) |
| `filelist-unreadable`, `filelist-glob-directory-unreadable`, `filelist-glob-directory-is-file` | A diagnostic that starts with `Trouble while reading file ${testcase}/.javadoc-index-files` ends with only the message of a Java exception, which can be a bare file name that does not say what went wrong, and which can name a file other than `.javadoc-index-files`. | [#450](https://github.com/plume-lib/javadoc-lookup/issues/450) |
