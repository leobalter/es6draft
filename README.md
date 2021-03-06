es6draft
========

ECMAScript 2015 (ECMA-262 6th Edition) compiler and runtime written in Java.

[![Build Status](https://travis-ci.org/anba/es6draft.png?branch=master)](https://travis-ci.org/anba/es6draft)

[![Build Status](https://buildhive.cloudbees.com/job/anba/job/es6draft/badge/icon)](https://buildhive.cloudbees.com/job/anba/job/es6draft/)

## Implementation Status ##

Full support of [ECMAScript® 2015] [es2015].

Full support of [ECMAScript® 2015 Internationalization API, ECMA-402 2nd edition] [intl].


## Build Instructions and Shell ##

### Prerequisites ###

* Download and install [JDK 8 or later] [java]
* Download and install [Apache Maven 3 or later] [maven]
* Set-up the `JAVA_HOME` environment variable to point to the JDK installation directory

### Build Instructions ###

* Clone this repository `git clone https://github.com/anba/es6draft.git && cd es6draft`
* Create the executable with `mvn package`

### Shell ###

* Start the shell using either `./bin/es6draft` or `.\bin\es6draft.bat` on Windows&reg;.
* `./bin/es6draft --help` prints the available options.

### Downloads ###

Prebuilt packages are available [here] [builds]. The zip archive contains the full distribution,
simply unzip the archive, start the shell, and you are ready to go!


## Test Suites ##

### Built-in Test Suite ###

The built-in test suite is run when no other Maven profile was selected. In other words, it is run
when the command `mvn test` is used.


### Test262 Test Suite ###

To start the [Test262] [test262] test runner select the `test262` Maven profile and set the
`TEST262_PATH` environment variable or use the `-Dtest262.path` parameter.

```
export TEST262_PATH = <test262 main directory>
mvn test -P test262

or:
mvn test -P test262 -Dtest262.path=<test262 main directory>
```

The `-Dtest262.include` parameter can be used to select specific test cases:
```
mvn test -P test262 -Dtest262.include="test/built-ins/Array/prototype/**/*.js"

mvn test -P test262 -Dtest262.include="test/built-ins/{Boolean\,Number}/prototype/**/*.js"
```

The `-Dtest262.exclude` parameter allows to exclude test cases:
```
mvn test -P test262 -Dtest262.exclude="test/built-ins/**/*.js"
```


### External Tests ###

Additional test suites are available to run tests from [Mozilla] [mozilla], [Traceur] [traceur],
[V8] [v8] and [WebKit] [webkit] using the `external` Maven profile.

The following environment variables need to be set to run these tests:
* `MOZILLA_PATH`: Mozilla-Central main directory
* `TRACEUR_PATH`: Traceur main directory
* `V8_PATH`: V8 main directory
* `WEBKIT_PATH`: WebKit main directory

Alternatively, the `-Dmozilla.path=...`, `-Dtraceur.path=...`, `-Dv8.path=...` and `-Dwebkit.path=...` parameters can be used.

To skip an external test, use `-D<name>.skip=true`. For example to run only the Traceur feature tests, use:
```
mvn test -P external -Dtraceur.path=<...> -Dmozilla.skip=true -Dv8.skip=true -Dwebkit.skip=true
```

[es2015]: http://ecma-international.org/publications/standards/Ecma-262.htm "ECMAScript® 2015 Language Specification"
[intl]: http://ecma-international.org/publications/standards/Ecma-402.htm "ECMAScript® 2015 Internationalization API Specification"
[icu]: http://site.icu-project.org/
[java]: http://java.sun.com/
[maven]: https://maven.apache.org/download.cgi
[test262]: https://github.com/tc39/test262/
[mozilla]: https://github.com/mozilla/gecko-dev/
[traceur]: https://github.com/google/traceur-compiler/
[v8]: https://github.com/v8/v8/
[webkit]: https://www.webkit.org/building/checkout.html
[builds]: https://buildhive.cloudbees.com/job/anba/job/es6draft/lastSuccessfulBuild/com.github.anba$es6draft/artifact/com.github.anba/es6draft/0.0.1-SNAPSHOT/
