/**
 * Copyright (c) 2012-2015 André Bargull
 * Alle Rechte vorbehalten / All Rights Reserved.  Use is subject to license terms.
 *
 * <https://github.com/anba/es6draft>
 */
package com.github.anba.es6draft.v8;

import static com.github.anba.es6draft.util.Resources.loadConfiguration;
import static com.github.anba.es6draft.util.Resources.loadTests;
import static com.github.anba.es6draft.v8.V8TestGlobalObject.newGlobalObjectAllocator;
import static org.junit.Assume.assumeTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.configuration.Configuration;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.junit.runners.Parameterized.UseParametersRunnerFactory;

import com.github.anba.es6draft.repl.console.ShellConsole;
import com.github.anba.es6draft.runtime.internal.CompatibilityOption;
import com.github.anba.es6draft.runtime.internal.ObjectAllocator;
import com.github.anba.es6draft.runtime.internal.ScriptCache;
import com.github.anba.es6draft.util.Functional.BiFunction;
import com.github.anba.es6draft.util.Functional.Function;
import com.github.anba.es6draft.util.Parallelized;
import com.github.anba.es6draft.util.ParameterizedRunnerFactory;
import com.github.anba.es6draft.util.TestConfiguration;
import com.github.anba.es6draft.util.TestGlobals;
import com.github.anba.es6draft.util.TestInfo;
import com.github.anba.es6draft.util.rules.ExceptionHandlers.ScriptExceptionHandler;
import com.github.anba.es6draft.util.rules.ExceptionHandlers.StandardErrorHandler;

/**
 *
 */
@RunWith(Parallelized.class)
@UseParametersRunnerFactory(ParameterizedRunnerFactory.class)
@TestConfiguration(name = "v8.test.mjsunit", file = "resource:/test-configuration.properties")
public final class MiniJSUnitTest {
    private static final Configuration configuration = loadConfiguration(MiniJSUnitTest.class);

    @Parameters(name = "{0}")
    public static List<V8TestInfo> suiteValues() throws IOException {
        return loadTests(configuration,
                new Function<Path, BiFunction<Path, Iterator<String>, V8TestInfo>>() {
                    @Override
                    public TestInfos apply(Path basedir) {
                        return new TestInfos(basedir);
                    }
                });
    }

    @ClassRule
    public static TestGlobals<V8TestGlobalObject, TestInfo> globals = new TestGlobals<V8TestGlobalObject, TestInfo>(
            configuration) {
        @Override
        protected ObjectAllocator<V8TestGlobalObject> newAllocator(ShellConsole console,
                TestInfo test, ScriptCache scriptCache) {
            return newGlobalObjectAllocator(console, test, scriptCache);
        }

        @Override
        protected Set<CompatibilityOption> getOptions() {
            EnumSet<CompatibilityOption> options = EnumSet.copyOf(super.getOptions());
            options.add(CompatibilityOption.ArrayIncludes);
            options.add(CompatibilityOption.ArrayBufferMissingLength);
            return options;
        }
    };

    @Rule
    public Timeout maxTime = new Timeout(120, TimeUnit.SECONDS);

    @Rule
    public ErrorCollector collector = new ErrorCollector();

    @Rule
    public StandardErrorHandler errorHandler = new StandardErrorHandler();

    @Rule
    public ScriptExceptionHandler exceptionHandler = new ScriptExceptionHandler();

    @Parameter(0)
    public V8TestInfo test;

    private static final class V8TestInfo extends TestInfo {
        private boolean module = false;

        public V8TestInfo(Path basedir, Path file) {
            super(basedir, file);
        }

        @Override
        public boolean isModule() {
            return module;
        }
    }

    private V8TestGlobalObject global;

    @Before
    public void setUp() throws Throwable {
        assumeTrue("Test disabled", test.isEnabled());

        global = globals.newGlobal(new V8TestConsole(collector), test);
        exceptionHandler.setExecutionContext(global.getRealm().defaultContext());
    }

    @After
    public void tearDown() {
        if (global != null) {
            global.getScriptLoader().getExecutor().shutdown();
        }
    }

    @Test
    public void runTest() throws Throwable {
        if (test.isModule()) {
            global.eval(test.toModuleName());
        } else {
            global.eval(test.getScript(), test.toFile());
        }

        // Wait for pending tasks to finish
        global.getRealm().getWorld().runEventLoop();
    }

    private static final class TestInfos implements BiFunction<Path, Iterator<String>, V8TestInfo> {
        private static final Pattern FlagsPattern = Pattern.compile("\\s*//\\s*Flags:\\s*(.*)\\s*");
        private final Path basedir;

        public TestInfos(Path basedir) {
            this.basedir = basedir;
        }

        @Override
        public V8TestInfo apply(Path file, Iterator<String> lines) {
            V8TestInfo test = new V8TestInfo(basedir, file);
            Pattern p = FlagsPattern;
            while (lines.hasNext()) {
                String line = lines.next();
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
                            // don't run debug-mode tests
                            test.setEnabled(false);
                        } else if (flag.startsWith("--expose-natives-as")) {
                            // don't run tests with natives or lazy compilation
                            if (flag.equals("--expose-natives-as")) {
                                // two arguments form, consume next argument as well
                                ++i;
                            }
                            test.setEnabled(false);
                        } else if (flag.equals("--expose-externalize-string")) {
                            // don't run tests with externalize-string
                            test.setEnabled(false);
                        } else if (flag.equals("--allow-natives-syntax")) {
                            // don't run tests with native syntax
                            test.setEnabled(false);
                        } else if (flag.equals("--lazy")) {
                            // don't run tests with lazy compilation
                            test.setEnabled(false);
                        } else if (flag.equals("--expose-trigger-failure")) {
                            // don't run tests with trigger-failure
                            test.setEnabled(false);
                        } else {
                            // ignore other flags
                        }
                    }
                } else if (line.equals("// MODULE")) {
                    test.module = true;
                }
            }
            return test;
        }
    }
}
