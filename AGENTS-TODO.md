# Plan: improve the end-to-end tests of CreateJavadocIndex

**This is a temporary plan file.  Delete it before any pull request is merged.**

Goal: in the git worktree
`/home/mernst/java/plume-lib/javadoc-lookup-fork-mernst-branch-e2e-tests`
(branch `e2e-tests`), add file-based end-to-end tests for `CreateJavadocIndex`
in the existing format (`src/test/resources/testdata/`, its `README.md`, and the
JUnit harness `CreateJavadocIndexTest.java`).  The tests must be data in files,
so that they can be run against an implementation in another language.  Do not
change the program's main source code.  Record a bug as current behavior in the
goal files, and list it in the "known problems" table of the README.  Commit at
each milestone (do not push, do not create a pull request, do not rewrite
history).  `./gradlew build` must pass at the end.  When every entry below is
done, delete this file and commit the deletion.

Findings so far:  the suite already has 59 test cases and no other end-to-end
tests (no shell scripts, no hard-coded Java tests), so there is nothing to
convert.

## Checklist

- [x] Harness: optional `-Pe2e.command=...` to run an external command instead
  of this implementation; document it in the README.
- [x] Harness: support for unreadable input files (`unreadable.txt` lists files
  that the harness makes unreadable in a copy of the test case), if cheap.
- [ ] README: language-neutral description of the invocation (working
  directory, arguments, home directory, environment), the compared outputs, and
  the normalization (including how a non-Java implementation handles the
  stack-trace normalization).
- [x] New test cases, file list: CRLF line endings, UTF-8 byte order mark, a
  `#` that is not at the start of a line, `~` that is not expanded, a glob that
  matches a hidden file.
- [x] New test cases, HTML encodings: UTF-8 byte order mark, UTF-16 with a byte
  order mark, invalid UTF-8 bytes.
- [x] New test cases, arguments: a space in a file name, an option-like
  argument (`--help`), an unreadable argument file.
- [x] New test cases, references: `http:` in the middle of a relative href; an
  anchor that is matched by two rules; a span with two anchors.
- [x] New test cases, ignored prefixes: `java.base` that is a file;
  `org.eclipse.jgit` that is a file.
- [ ] Update the README coverage list and known-problems table.
- [ ] `./gradlew spotlessApply build` passes; markdownlint-cli2 passes.
- [ ] Delete this file and commit.
