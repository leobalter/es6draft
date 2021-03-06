/**
 * Copyright (c) 2012-2015 André Bargull
 * Alle Rechte vorbehalten / All Rights Reserved.  Use is subject to license terms.
 *
 * <https://github.com/anba/es6draft>
 */
package com.github.anba.es6draft.util.rules;

import static com.github.anba.es6draft.util.Functional.iterable;
import static com.github.anba.es6draft.util.Functional.map;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.instanceOf;

import org.hamcrest.Matcher;

import com.github.anba.es6draft.compiler.CompilationException;
import com.github.anba.es6draft.parser.ParserException;
import com.github.anba.es6draft.repl.global.StopExecutionException;
import com.github.anba.es6draft.runtime.ExecutionContext;
import com.github.anba.es6draft.runtime.internal.ScriptException;
import com.github.anba.es6draft.util.Functional.Function;
import com.github.anba.es6draft.util.TestAssertions;

/**
 * Default exception handlers.
 */
public final class ExceptionHandlers {
    private ExceptionHandlers() {
    }

    /**
     * anyOf(asList(types).map(x -> instanceOf(x)))
     */
    private static final Matcher<Object> anyInstanceOf(final Class<?>... types) {
        return anyOf(map(iterable(types), new Function<Class<?>, Matcher<? super Object>>() {
            @Override
            public Matcher<? super Object> apply(Class<?> type) {
                return instanceOf(type);
            }
        }));
    }

    /**
     * {@link ExceptionHandler} for {@link ParserException}, {@link CompilationException} and
     * {@link StackOverflowError} errors.
     */
    public static final class StandardErrorHandler extends ExceptionHandler {
        private static final Matcher<Object> defaultMatcher = anyInstanceOf(ParserException.class,
                CompilationException.class, StackOverflowError.class);

        public StandardErrorHandler() {
            this(defaultMatcher());
        }

        public StandardErrorHandler(Matcher<?> matcher) {
            super(matcher);
        }

        @Override
        protected void handle(Throwable t) {
            if (t instanceof ParserException) {
                throw TestAssertions.toAssertionError((ParserException) t);
            }
            throw new AssertionError(t.getMessage(), t);
        }

        public static StandardErrorHandler none() {
            return new StandardErrorHandler(nothing());
        }

        public static Matcher<Object> defaultMatcher() {
            return defaultMatcher;
        }
    }

    /**
     * {@link ExceptionHandler} for {@link ScriptException} errors.
     */
    public static final class ScriptExceptionHandler extends ExceptionHandler {
        private static final Matcher<Object> defaultMatcher = instanceOf(ScriptException.class);

        private ExecutionContext cx;

        public ScriptExceptionHandler() {
            this(defaultMatcher());
        }

        public ScriptExceptionHandler(Matcher<?> matcher) {
            super(matcher);
        }

        public void setExecutionContext(ExecutionContext cx) {
            this.cx = cx;
        }

        @Override
        protected void handle(Throwable t) {
            throw TestAssertions.toAssertionError(cx, (ScriptException) t);
        }

        public static ScriptExceptionHandler none() {
            return new ScriptExceptionHandler(nothing());
        }

        public static Matcher<Object> defaultMatcher() {
            return defaultMatcher;
        }
    }

    /**
     * {@link ExceptionHandler} for {@link ParserException}, {@link CompilationException},
     * {@link StackOverflowError} and {@link ScriptException} errors.
     */
    public static final class IgnoreExceptionHandler extends ExceptionHandler {
        private static final Matcher<Object> defaultMatcher = anyInstanceOf(ParserException.class,
                CompilationException.class, StackOverflowError.class, ScriptException.class);

        public IgnoreExceptionHandler() {
            this(defaultMatcher());
        }

        public IgnoreExceptionHandler(Matcher<?> matcher) {
            super(matcher);
        }

        @Override
        protected void handle(Throwable t) {
            // Ignore any errors
        }

        public static IgnoreExceptionHandler none() {
            return new IgnoreExceptionHandler(nothing());
        }

        public static Matcher<Object> defaultMatcher() {
            return defaultMatcher;
        }
    }

    /**
     * {@link ExceptionHandler} for {@link StopExecutionException} errors.
     */
    public static final class StopExecutionHandler extends ExceptionHandler {
        private static final Matcher<Object> defaultMatcher = instanceOf(StopExecutionException.class);

        public StopExecutionHandler() {
            super(defaultMatcher());
        }

        @Override
        protected void handle(Throwable t) {
            // ignore
        }

        public static Matcher<Object> defaultMatcher() {
            return defaultMatcher;
        }
    }
}
