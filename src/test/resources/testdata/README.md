# End-to-end test data for CreateJavadocIndex

Each subdirectory of this directory is one end-to-end test case for
`CreateJavadocIndex`.  `CreateJavadocIndexTest` runs the program once per
subdirectory, in a subprocess, and compares the program's output to goal files.

The program is run with the test case directory as both its current directory and
its home directory (that is, as `user.home`).  Therefore, every file name in a test
case -- in `args.txt`, in `.javadoc-index-files`, and in the goal files -- is
relative to the test case directory.

## Files in a test case directory

| File | Meaning |
| --- | --- |
| `args.txt` | The command-line arguments, one per line.  Blank lines and lines that start with `#` are ignored.  In an argument, `${testcase}` is replaced by the absolute file name of the test case directory, which lets a test case pass an absolute file name.  If this file is absent, the program is run with no command-line arguments, which makes it read `.javadoc-index-files`. |
| `.javadoc-index-files` | The list of API documentation files that the program reads when it is given no command-line arguments. |
| `expected-stdout.txt` | Goal file for the program's standard output.  Required. |
| `expected-stderr.txt` | Goal file for the program's standard error.  If absent, the program must write nothing to standard error. |
| `expected-status.txt` | Goal file for the program's exit status.  If absent, the program must exit with status 0. |

All other files in a test case directory are input: HTML API documentation files,
and stub files that exist only so that git records a directory whose mere existence
affects the program's behavior.

## Normalization of the program's output

Before comparing the program's output to a goal file, the test makes two
substitutions, so that the goal files do not depend on where the repository is
checked out nor on the exact version of the program:

* The absolute file name of the test case directory is replaced by `${testcase}`.
* Each stack trace is replaced by the single line `<TAB>at ...`.

The goal files spell file names in the Unix style, with `/` as the separator, so the
tests are skipped on Windows.

## Updating the goal files

Run

```
./gradlew test -DupdateGoals=true
```

to overwrite the goal files with the program's current output.  Always inspect the
resulting diff before committing it: a change in the program's output is exactly
what these tests exist to detect.

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
* The target of a reference: `href-external`, `href-missing`, `href-scheme`,
  `relative-href`.
* The ignored prefixes: `jdk-modules`, `jdk-modules-split`, `jgit`, `jgit-split`,
  `index-files-at-root`, `arg-dot-slash`, `arg-absolute`.
* Command-line arguments: `arg-directory`, `arg-duplicate`,
  `no-directory-in-argument`, `nonexistent-argument`, `two-libraries`.
* The `.javadoc-index-files` file, including globbing: the `filelist-*` test cases.
* Degenerate input: `empty-file`, `empty-index`, `malformed-html`, `not-html`,
  `charset-iso-8859-1`, `missing-anchor`, `duplicate-entries`.
* The form of the output: `key-sorting`.

## The goal files record the current behavior, not the desired behavior

A goal file was created by running the program, so it records what the program does
today.  In several test cases that is not what the program ought to do.  Each such
test case has a comment in its input that says so.  The known problems are:

| Test case | Problem |
| --- | --- |
| `quote-in-symbol` | A backslash in a symbol is not escaped in the Emacs Lisp output. |
| `entity-in-symbol` | Only the entities `&lt;` and `&gt;` are decoded, so a symbol can contain the literal text `&amp;` or `&nbsp;`. |
| `code-element-in-symbol` | A `<code>` start tag that has an attribute is not stripped. |
| `span-element-in-symbol` | A `<span>` start tag that lacks a `class` attribute is not stripped, although the matching `</span>` end tag is. |
| `other-markup-in-symbol` | Markup other than `code` and `span` is not stripped. |
| `charset-iso-8859-1` | The program passes `"UTF-8"` to Jsoup, which makes Jsoup ignore the charset that the document declares. |
| `href-scheme` | The test for an external reference is case-sensitive and knows only `http:` and `https:`, so `HTTPS:`, `//`, `ftp:`, `mailto:`, and `file:` yield a nonexistent local file name. |
| `href-missing` | An `<a>` element with no `href` attribute yields a reference to the directory that contains the index file, rather than a diagnostic. |
| `arg-dot-slash` | The ignored prefix is not normalized, so for the argument `./api/index-all.html` the ignored prefix `file:./api/` matches none of the references, which are under `file:api/`. |
| `at-sign-in-symbol` | Stripping an annotation leaves the space that followed it, and a symbol that is a lone `@` becomes the empty string. |
| `filelist-glob-no-match` | A glob that matches no file is silently ignored. |
| `filelist-glob-in-directory-name` | A glob in a directory name is silently ignored. |
| `filelist-glob-metacharacters` | A line is treated as a glob only if it contains `*`, so `?`, `[...]`, and `{...}` are not expanded even though `Files.newDirectoryStream` supports them. |
| `arg-directory`, `filelist-glob-matches-directory` | Naming a directory rather than a file produces an uncaught exception and a stack trace, and discards the entries that were read from the files that were processed first. |
