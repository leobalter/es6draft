/**
 * Copyright (c) 2012-2015 André Bargull
 * Alle Rechte vorbehalten / All Rights Reserved.  Use is subject to license terms.
 *
 * <https://github.com/anba/es6draft>
 */
package com.github.anba.es6draft.traceur;

import static com.github.anba.es6draft.traceur.TraceurTestGlobalObject.newGlobalObjectAllocator;
import static com.github.anba.es6draft.util.Resources.loadConfiguration;
import static com.github.anba.es6draft.util.Resources.loadTests;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.configuration.Configuration;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.junit.runners.Parameterized.UseParametersRunnerFactory;

import com.github.anba.es6draft.parser.Parser;
import com.github.anba.es6draft.repl.console.ShellConsole;
import com.github.anba.es6draft.runtime.extensions.timer.Timers;
import com.github.anba.es6draft.runtime.internal.CompatibilityOption;
import com.github.anba.es6draft.runtime.internal.ObjectAllocator;
import com.github.anba.es6draft.runtime.internal.Properties;
import com.github.anba.es6draft.runtime.internal.ScriptCache;
import com.github.anba.es6draft.runtime.internal.ScriptLoader;
import com.github.anba.es6draft.runtime.modules.ResolutionException;
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
@TestConfiguration(name = "traceur.test", file = "resource:/test-configuration.properties")
public final class TraceurTest {
    private static final Configuration configuration = loadConfiguration(TraceurTest.class);

    @Parameters(name = "{0}")
    public static List<TraceurTestInfo> suiteValues() throws IOException {
        return loadTests(configuration,
                new Function<Path, BiFunction<Path, Iterator<String>, TraceurTestInfo>>() {
                    @Override
                    public TestInfos apply(Path basedir) {
                        return new TestInfos(basedir);
                    }
                });
    }

    @ClassRule
    public static TestGlobals<TraceurTestGlobalObject, TraceurTestInfo> globals = new TestGlobals<TraceurTestGlobalObject, TraceurTestInfo>(
            configuration) {
        @Override
        protected Set<CompatibilityOption> getOptions() {
            EnumSet<CompatibilityOption> options = EnumSet.copyOf(super.getOptions());
            options.add(CompatibilityOption.AsyncFunction);
            options.add(CompatibilityOption.Exponentiation);
            options.add(CompatibilityOption.Comprehension);
            options.add(CompatibilityOption.ExportFrom);
            return options;
        }

        @Override
        protected ObjectAllocator<TraceurTestGlobalObject> newAllocator(ShellConsole console,
                TraceurTestInfo test, ScriptCache scriptCache) {
            return newGlobalObjectAllocator(console, test, scriptCache);
        }

        @Override
        protected TraceurFileModuleLoader createModuleLoader(ScriptLoader scriptLoader) {
            return new TraceurFileModuleLoader(scriptLoader, getBaseDirectory());
        }
    };

    @Rule
    public Timeout maxTime = new Timeout(120, TimeUnit.SECONDS);

    @Rule
    public StandardErrorHandler errorHandler = StandardErrorHandler.none();

    @Rule
    public ScriptExceptionHandler exceptionHandler = ScriptExceptionHandler.none();

    @Rule
    public ExpectedException expected = ExpectedException.none();

    @Parameter(0)
    public TraceurTestInfo test;

    private static final class TraceurTestInfo extends TestInfo {
        boolean negative = false;
        boolean async = false;
        boolean tailCall = false;

        TraceurTestInfo(Path basedir, Path script) {
            super(basedir, script);
        }

        @Override
        public boolean isModule() {
            return getScript().getFileName().toString().endsWith(".module.js");
        }

        @Override
        public String toModuleName() {
            String moduleName = super.toModuleName();
            assert moduleName.endsWith(".js");
            return moduleName.substring(0, moduleName.length() - 3);
        }
    }

    private TraceurTestGlobalObject global;
    private AsyncHelper async;
    private Timers timers;

    @Before
    public void setUp() throws Throwable {
        assumeTrue("Test disabled", test.isEnabled());

        global = globals.newGlobal(new TraceurConsole(), test);
        exceptionHandler.setExecutionContext(global.getRealm().defaultContext());
        if (test.async) {
            async = global.install(new AsyncHelper(), AsyncHelper.class);
            timers = global.install(new Timers(), Timers.class);
        }
        if (test.negative) {
            if (test.isModule()) {
                expected.expect(Matchers.either(StandardErrorHandler.defaultMatcher())
                        .or(ScriptExceptionHandler.defaultMatcher())
                        .or(Matchers.instanceOf(ResolutionException.class))
                        .or(Matchers.instanceOf(NoSuchFileException.class)));
            } else {
                expected.expect(Matchers.either(StandardErrorHandler.defaultMatcher()).or(
                        ScriptExceptionHandler.defaultMatcher()));
            }
        } else {
            errorHandler.match(StandardErrorHandler.defaultMatcher());
            exceptionHandler.match(ScriptExceptionHandler.defaultMatcher());
        }
        if (test.tailCall) {
            global.getScriptLoader().getParserOptions().add(Parser.Option.Strict);
        }
    }

    @After
    public void tearDown() throws InterruptedException {
        if (global != null) {
            global.getScriptLoader().getExecutor().shutdown();
        }
    }

    @Test
    public void runTest() throws Throwable {
        // evaluate actual test-script
        if (test.isModule()) {
            global.eval(test.toModuleName());
        } else {
            global.eval(test.getScript(), test.toFile());
        }

        // wait for pending tasks to finish
        if (test.async) {
            assertFalse(async.doneCalled);
            global.getRealm().getWorld().runEventLoop(timers);
            assertTrue(async.doneCalled);
        } else {
            global.getRealm().getWorld().runEventLoop();
        }
    }

    public static final class AsyncHelper {
        boolean doneCalled = false;

        @Properties.Function(name = "done", arity = 0)
        public void done() {
            assertFalse(doneCalled);
            doneCalled = true;
        }
    }

    private static final class TestInfos implements
            BiFunction<Path, Iterator<String>, TraceurTestInfo> {
        private static final Pattern FlagsPattern = Pattern.compile("\\s*//\\s*(.*)\\s*");
        private final Path basedir;

        public TestInfos(Path basedir) {
            this.basedir = basedir;
        }

        @Override
        public TraceurTestInfo apply(Path file, Iterator<String> lines) {
            TraceurTestInfo test = new TraceurTestInfo(basedir, file);
            Pattern p = FlagsPattern;
            while (lines.hasNext()) {
                String line = lines.next();
                Matcher m = p.matcher(line);
                if (m.matches()) {
                    String s = m.group(1);
                    if ("Only in browser.".equals(s) || s.startsWith("Skip.")) {
                        test.setEnabled(false);
                    } else if (s.equals("Async.")) {
                        test.async = true;
                    } else if (s.startsWith("Error:")) {
                        test.negative = true;
                    } else if (s.startsWith("Options:")) {
                        if (s.contains("--proper-tail-calls")) {
                            test.tailCall = true;
                        }
                        // ignore
                    } else {
                        break;
                    }
                } else {
                    break;
                }
            }
            return test;
        }
    }
}
