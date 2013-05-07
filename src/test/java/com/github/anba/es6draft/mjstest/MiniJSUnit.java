/**
 * Copyright (c) 2012-2013 André Bargull
 * Alle Rechte vorbehalten / All Rights Reserved.  Use is subject to license terms.
 *
 * <https://github.com/anba/es6draft>
 */
package com.github.anba.es6draft.mjstest;

import static com.github.anba.es6draft.mjstest.MiniJSUnitGlobalObject.newGlobal;
import static java.util.Arrays.asList;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeThat;
import static org.junit.Assume.assumeTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.junit.runners.model.MultipleFailureException;

import com.github.anba.es6draft.Script;
import com.github.anba.es6draft.parser.ParserException;
import com.github.anba.es6draft.runtime.internal.ScriptCache;
import com.github.anba.es6draft.runtime.internal.ScriptException;
import com.github.anba.es6draft.util.Parallelized;

/**
 *
 */
@RunWith(Parallelized.class)
public class MiniJSUnit {

    /**
     * Returns a {@link Path} which points to the test directory 'v8.test.mjsunit'
     */
    private static Path testDir() {
        String testPath = System.getenv("V8_MJSUNIT");
        return (testPath != null ? Paths.get(testPath) : null);
    }

    @Parameters(name = "{0}")
    public static Iterable<Object[]> suiteValues() throws IOException {
        Path testdir = testDir();
        assumeThat("missing system property 'V8_MJSUNIT'", testdir, notNullValue());
        assumeTrue("directy 'V8_MJSUNIT' does not exist", Files.exists(testdir));
        List<TestInfo> tests = filterTests(loadTests(testdir, testdir), "/mjsunit.list");
        return toObjectArray(tests);
    }

    private static ScriptCache scriptCache = new ScriptCache();
    private static Script legacyJS;

    @Rule
    public Timeout maxTime = new Timeout((int) TimeUnit.SECONDS.toMillis(120));

    @Parameter(0)
    public TestInfo test;

    @ClassRule
    public static ExternalResource resource = new ExternalResource() {
        @Override
        protected void before() throws Throwable {
            legacyJS = MiniJSUnitGlobalObject.compileLegacy(scriptCache);
        }
    };

    @Test
    public void runTest() throws Throwable {
        TestInfo test = this.test;
        // filter disabled tests
        assumeTrue(test.enable);
        // don't run tests with natives or lazy compilation
        assumeFalse(test.natives || test.lazy);
        // don't run debug-mode tests
        assumeFalse(test.debug);

        // TODO: collect multiple failures
        List<Throwable> failures = new ArrayList<Throwable>();
        MiniJSUnitGlobalObject global = newGlobal(testDir(), test.script, scriptCache, legacyJS);

        // load and execute mjsunit.js file
        global.include(Paths.get("mjsunit.js"));

        // evaluate actual test-script
        Path js = testDir().resolve(test.script);
        try {
            global.eval(test.script, js);
        } catch (ParserException e) {
            // count towards the overall failure count
            String message = String.format("%s: %s", e.getExceptionType(), e.getMessage());
            failures.add(new AssertionError(message, e));
        } catch (ScriptException e) {
            // count towards the overall failure count
            String message = e.getMessage(global.getRealm().defaultContext());
            failures.add(new AssertionError(message, e));
        } catch (IOException e) {
            fail(e.getMessage());
        }

        // fail if any test returns with errors
        MultipleFailureException.assertEmpty(failures);
    }

    static class TestInfo {
        Path script;
        boolean debug = false;
        boolean natives = false;
        boolean lazy = false;
        boolean enable = true;

        @Override
        public String toString() {
            return script.toString();
        }
    }

    private static final Set<String> excludedSet = new HashSet<>(asList("mjsunit.js"));
    private static final Set<String> excludeDirs = new HashSet<>(asList("bugs", "tools"));

    private static List<TestInfo> loadTests(Path searchdir, final Path basedir) throws IOException {
        final List<TestInfo> tests = new ArrayList<>();
        Files.walkFileTree(searchdir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                    throws IOException {
                if (excludeDirs.contains(dir.getFileName().toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return super.preVisitDirectory(dir, attrs);
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                if (attrs.isRegularFile() && attrs.size() != 0L) {
                    String name = file.getFileName().toString();
                    if (!excludedSet.contains(name) && name.endsWith(".js")) {
                        TestInfo test = new TestInfo();
                        test.script = basedir.relativize(file);
                        try (BufferedReader reader = Files.newBufferedReader(file,
                                StandardCharsets.UTF_8)) {
                            applyFlagsInfo(test, reader);
                        }
                        tests.add(test);
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return tests;
    }

    private static final Pattern FlagsPattern = Pattern.compile("\\s*//\\s*Flags:\\s*(.*)\\s*");

    private static void applyFlagsInfo(TestInfo test, BufferedReader reader) throws IOException {
        Pattern p = FlagsPattern;
        for (String line; (line = reader.readLine()) != null;) {
            Matcher m = p.matcher(line);
            if (m.matches()) {
                String[] flags = m.group(1).split("\\s+");
                for (int i = 0, len = flags.length; i < len; ++i) {
                    String flag = flags[i].replace('_', '-');
                    if (flag.startsWith("--expose-debug-as")) {
                        if (flag.equals("--expose-debug-as")) {
                            // two arguments form, consume next argument as well
                            ++i;
                        }
                        test.debug = true;
                    } else if (flag.startsWith("--expose-natives-as")) {
                        if (flag.equals("--expose-natives-as")) {
                            // two arguments form, consume next argument as well
                            ++i;
                        }
                        test.natives = true;
                    } else if (flag.equals("--expose-externalize-string")) {
                        test.natives = true;
                    } else if (flag.equals("--allow-natives-syntax")) {
                        test.natives = true;
                    } else if (flag.equals("--lazy")) {
                        test.lazy = true;
                    } else {
                        // ignore other flags
                    }
                }
            }
        }
    }

    private static List<TestInfo> filterTests(List<TestInfo> tests, String filename)
            throws IOException {
        // list->map
        Map<Path, TestInfo> map = new LinkedHashMap<>();
        for (TestInfo test : tests) {
            map.put(test.script, test);
        }
        // disable tests
        List<TestInfo> disabledTests = new ArrayList<>();
        InputStream res = MiniJSUnit.class.getResourceAsStream(filename);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(res,
                StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("#") || line.isEmpty()) {
                    continue;
                }
                TestInfo t = map.get(Paths.get(line));
                if (t == null) {
                    System.err.printf("detected stale entry '%s'\n", line);
                    continue;
                }
                disabledTests.add(t);
                t.enable = false;
            }
        }
        System.out.printf("disabled %d tests of %d in total%n", disabledTests.size(), tests.size());
        return tests;
    }

    /**
     * {@link Parameterized} expects a list of {@code Object[]}
     */
    private static Iterable<Object[]> toObjectArray(Iterable<?> iterable) {
        List<Object[]> list = new ArrayList<Object[]>();
        for (Object o : iterable) {
            list.add(new Object[] { o });
        }
        return list;
    }
}