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
| `args.txt` | The command-line arguments, one per line.  Blank lines and lines that start with `#` are ignored.  If this file is absent, the program is run with no command-line arguments, which makes it read `.javadoc-index-files`. |
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
