/**
 * Copyright (c) 2012-2015 André Bargull
 * Alle Rechte vorbehalten / All Rights Reserved.  Use is subject to license terms.
 *
 * <https://github.com/anba/es6draft>
 */
package com.github.anba.es6draft.runtime.objects;

import static com.github.anba.es6draft.runtime.types.Undefined.UNDEFINED;

import java.util.EnumSet;

import com.github.anba.es6draft.Script;
import com.github.anba.es6draft.compiler.CompilationException;
import com.github.anba.es6draft.parser.Parser;
import com.github.anba.es6draft.parser.ParserException;
import com.github.anba.es6draft.runtime.ExecutionContext;
import com.github.anba.es6draft.runtime.Realm;
import com.github.anba.es6draft.runtime.internal.Source;
import com.github.anba.es6draft.runtime.types.Callable;
import com.github.anba.es6draft.runtime.types.Type;

/**
 * <h1>18 The Global Object</h1><br>
 * <h2>18.2 Function Properties of the Global Object</h2>
 * <ul>
 * <li>18.2.1 eval (x)
 * </ul>
 */
public final class Eval {
    private Eval() {
    }

    public enum EvalFlags {
        /**
         * Flag for direct eval calls
         */
        Direct(0x0001),

        /**
         * Flag for strict-mode eval calls
         */
        Strict(0x0002),

        /**
         * Flag for global code eval calls
         */
        GlobalCode(0x0004),

        /**
         * Flag for global scope eval calls
         */
        GlobalScope(0x0008),

        /**
         * Flag for global this eval calls
         */
        GlobalThis(0x0010),

        /**
         * Flag for eval calls enclosed by with-statement
         */
        EnclosedByWithStatement(0x0020),

        /**
         * Flag for eval calls enclosed by lexical declaration
         */
        EnclosedByLexicalDeclaration(0x0040);

        private final int value;

        private EvalFlags(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        boolean isSet(int bitmask) {
            return (value & bitmask) != 0;
        }

        static EnumSet<Parser.Option> toOptions(int flags) {
            EnumSet<Parser.Option> options = EnumSet.of(Parser.Option.EvalScript);
            if (EvalFlags.Direct.isSet(flags)) {
                options.add(Parser.Option.DirectEval);
            }
            if (EvalFlags.Strict.isSet(flags)) {
                options.add(Parser.Option.Strict);
            }
            if (!EvalFlags.GlobalCode.isSet(flags)) {
                options.add(Parser.Option.FunctionCode);
            }
            if (!EvalFlags.GlobalScope.isSet(flags)) {
                options.add(Parser.Option.LocalScope);
            }
            if (!EvalFlags.GlobalThis.isSet(flags)) {
                options.add(Parser.Option.FunctionThis);
            }
            if (EvalFlags.EnclosedByWithStatement.isSet(flags)) {
                options.add(Parser.Option.EnclosedByWithStatement);
            }
            if (EvalFlags.EnclosedByLexicalDeclaration.isSet(flags)) {
                options.add(Parser.Option.EnclosedByLexicalDeclaration);
            }
            return options;
        }

        public static int toFlags(EnumSet<Parser.Option> options) {
            int flags = 0;
            if (options.contains(Parser.Option.DirectEval)) {
                flags |= EvalFlags.Direct.getValue();
            }
            if (options.contains(Parser.Option.Strict)) {
                flags |= EvalFlags.Strict.getValue();
            }
            if (!options.contains(Parser.Option.FunctionCode)) {
                flags |= EvalFlags.GlobalCode.getValue();
            }
            if (!options.contains(Parser.Option.LocalScope)) {
                flags |= EvalFlags.GlobalScope.getValue();
            }
            if (!options.contains(Parser.Option.FunctionThis)) {
                flags |= EvalFlags.GlobalThis.getValue();
            }
            if (options.contains(Parser.Option.EnclosedByWithStatement)) {
                flags |= EvalFlags.EnclosedByWithStatement.getValue();
            }
            if (options.contains(Parser.Option.EnclosedByLexicalDeclaration)) {
                flags |= EvalFlags.EnclosedByLexicalDeclaration.getValue();
            }
            return flags;
        }
    }

    /**
     * 18.2.1 eval (x)
     * 
     * @param cx
     *            the execution context
     * @param caller
     *            the caller context
     * @param source
     *            the source string
     * @return the evaluation result
     */
    public static Object globalEval(ExecutionContext cx, ExecutionContext caller, Object source) {
        return PerformEval(cx, caller, source, EvalFlags.GlobalCode.getValue()
                | EvalFlags.GlobalScope.getValue() | EvalFlags.GlobalThis.getValue());
    }

    /**
     * 18.2.1 eval (x)
     * 
     * @param cx
     *            the execution context
     * @param caller
     *            the caller context
     * @param arguments
     *            the arguments
     * @return the evaluation result
     */
    public static Object indirectEval(ExecutionContext cx, ExecutionContext caller,
            Object... arguments) {
        Object source;
        Callable indirectEval = cx.getRealm().getIndirectEval();
        if (indirectEval != null) {
            source = indirectEval.call(cx, cx.getRealm().getRealmObject(), arguments);
        } else {
            source = arguments.length > 0 ? arguments[0] : UNDEFINED;
        }
        return globalEval(cx, caller, source);
    }

    /**
     * 18.2.1 eval (x)
     * <p>
     * [Called from generated code]
     * 
     * @param arguments
     *            the arguments
     * @param cx
     *            the execution context
     * @param flags
     *            the eval flags
     * @return the evaluation result
     */
    public static Object directEval(Object[] arguments, ExecutionContext cx, int flags) {
        assert EvalFlags.Direct.isSet(flags);
        Object source;
        Callable translate = cx.getRealm().getDirectEvalTranslate();
        if (translate != null) {
            source = translate.call(cx, cx.getRealm().getRealmObject(), arguments);
        } else {
            source = arguments.length > 0 ? arguments[0] : UNDEFINED;
        }
        return PerformEval(cx, cx, source, flags);
    }

    /**
     * 18.2.1 eval (x)
     * <p>
     * [Called from generated code]
     * 
     * @param source
     *            the eval source code
     * @param cx
     *            the execution context
     * @param flags
     *            the eval flags
     * @return the evaluation result
     */
    public static Object directEval(Object source, ExecutionContext cx, int flags) {
        assert EvalFlags.Direct.isSet(flags);
        return PerformEval(cx, cx, source, flags);
    }

    /**
     * 18.2.1.1 Runtime Semantics: PerformEval( x, evalRealm, strictCaller, direct)
     * 
     * @param cx
     *            the execution context
     * @param caller
     *            the caller execution context
     * @param source
     *            the source string
     * @param flags
     *            the eval flags
     * @return the evaluation result
     */
    private static Object PerformEval(ExecutionContext cx, ExecutionContext caller, Object source,
            int flags) {
        boolean strictCaller = EvalFlags.Strict.isSet(flags);
        boolean direct = EvalFlags.Direct.isSet(flags);
        assert direct || cx == cx.getRealm().defaultContext() : "indirect eval with non-default context";

        /* step 1 */
        assert direct || !strictCaller;
        /* step 2 */
        if (!Type.isString(source)) {
            return source;
        }
        /* step 3 */
        Script script = script(cx, caller, Type.stringValue(source).toString(), flags);
        /* step 4 */
        if (script == null) {
            return UNDEFINED;
        }
        /* steps 5-23 */
        return script.evaluate(cx);
    }

    private static Script script(ExecutionContext cx, ExecutionContext caller, String sourceCode,
            int flags) {
        try {
            Realm realm = cx.getRealm();
            Source source = evalSource(realm, caller);
            EnumSet<Parser.Option> options = EvalFlags.toOptions(flags);
            return realm.getScriptLoader().evalScript(source, sourceCode, options);
        } catch (ParserException | CompilationException e) {
            throw e.toScriptException(cx);
        }
    }

    private static Source evalSource(Realm realm, ExecutionContext caller) {
        Source baseSource = realm.sourceInfo(caller);
        String sourceName;
        if (baseSource != null) {
            sourceName = "<eval> (" + baseSource.getName() + ")";
        } else {
            sourceName = "<eval>";
        }
        return new Source(baseSource, sourceName, 1);
    }
}
