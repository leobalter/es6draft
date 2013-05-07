/**
 * Copyright (c) 2012-2013 André Bargull
 * Alle Rechte vorbehalten / All Rights Reserved.  Use is subject to license terms.
 *
 * <https://github.com/anba/es6draft>
 */
package com.github.anba.es6draft.repl;

import static com.github.anba.es6draft.repl.SourceBuilder.ToSource;
import static com.github.anba.es6draft.repl.WrapperProxy.CreateWrapProxy;
import static com.github.anba.es6draft.runtime.AbstractOperations.*;
import static com.github.anba.es6draft.runtime.internal.Errors.throwReferenceError;
import static com.github.anba.es6draft.runtime.internal.Errors.throwSyntaxError;
import static com.github.anba.es6draft.runtime.internal.Properties.createProperties;
import static com.github.anba.es6draft.runtime.internal.ScriptRuntime._throw;
import static com.github.anba.es6draft.runtime.types.Undefined.UNDEFINED;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import com.github.anba.es6draft.Script;
import com.github.anba.es6draft.ScriptLoader;
import com.github.anba.es6draft.ast.ExpressionStatement;
import com.github.anba.es6draft.ast.FunctionNode;
import com.github.anba.es6draft.parser.Parser;
import com.github.anba.es6draft.parser.ParserException;
import com.github.anba.es6draft.parser.ParserException.ExceptionType;
import com.github.anba.es6draft.runtime.ExecutionContext;
import com.github.anba.es6draft.runtime.Realm;
import com.github.anba.es6draft.runtime.Realm.GlobalObjectCreator;
import com.github.anba.es6draft.runtime.internal.Properties.Function;
import com.github.anba.es6draft.runtime.internal.ScriptCache;
import com.github.anba.es6draft.runtime.internal.ScriptException;
import com.github.anba.es6draft.runtime.objects.ErrorConstructor;
import com.github.anba.es6draft.runtime.objects.FunctionPrototype;
import com.github.anba.es6draft.runtime.objects.GlobalObject;
import com.github.anba.es6draft.runtime.types.BuiltinSymbol;
import com.github.anba.es6draft.runtime.types.Callable;
import com.github.anba.es6draft.runtime.types.Intrinsics;
import com.github.anba.es6draft.runtime.types.ScriptObject;
import com.github.anba.es6draft.runtime.types.Type;
import com.github.anba.es6draft.runtime.types.builtins.ExoticProxy;
import com.github.anba.es6draft.runtime.types.builtins.ExoticSymbolObject;

/**
 * Global object class with support for multiple moz-shell functions
 */
public final class MozShellGlobalObject extends GlobalObject {
    private final long startMilli = System.currentTimeMillis();
    private final long startNano = System.nanoTime();
    private final Realm realm;
    private final Path basedir;
    private final Path script;
    private final Path libdir;
    private final ScriptCache scriptCache;
    private final Script initScript;
    private final MozShellConsole console;

    private MozShellGlobalObject(Realm realm, MozShellConsole console, Path basedir, Path script,
            Path libdir, ScriptCache scriptCache, Script initScript) {
        super(realm);
        this.realm = realm;
        this.console = console;
        this.basedir = basedir;
        this.script = script;
        this.libdir = libdir;
        this.scriptCache = scriptCache;
        this.initScript = initScript;
    }

    /**
     * Returns a new instance of this class
     */
    public static MozShellGlobalObject newGlobal(final MozShellConsole console, final Path baseDir,
            final Path script, final Path libdir, final ScriptCache scriptCache,
            final Script initScript) {
        Realm realm = Realm.newRealm(new GlobalObjectCreator<MozShellGlobalObject>() {
            @Override
            public MozShellGlobalObject createGlobal(Realm realm) {
                return new MozShellGlobalObject(realm, console, baseDir, script, libdir,
                        scriptCache, initScript);
            }
        });

        // start initialization
        ExecutionContext cx = realm.defaultContext();
        MozShellGlobalObject global = (MozShellGlobalObject) realm.getGlobalThis();
        createProperties(global, cx, MozShellGlobalObject.class);

        // load init-script
        if (global.initScript != null) {
            ScriptLoader.ScriptEvaluation(global.initScript, realm, false);
        }

        return global;
    }

    /**
     * Parses, compiles and executes the javascript file
     */
    public void eval(Path fileName, Path file) throws IOException, ParserException {
        Script script = scriptCache.script(fileName.toString(), 1, file);
        ScriptLoader.ScriptEvaluation(script, realm, false);
    }

    /**
     * Parses, compiles and executes the javascript file (uses {@link #scriptCache})
     */
    public void include(Path file) throws IOException, ParserException {
        Script script = scriptCache.get(absolutePath(file));
        ScriptLoader.ScriptEvaluation(script, realm, false);
    }

    /**
     * Compiles the "mozlegacy.js" script-file
     */
    public static Script compileLegacy(ScriptCache scriptCache) throws ParserException, IOException {
        String sourceName = "/scripts/mozlegacy.js";
        InputStream stream = MozShellGlobalObject.class.getResourceAsStream(sourceName);
        return scriptCache.script(sourceName, 1, stream);
    }

    private static ScriptException throwError(Realm realm, String message) {
        ErrorConstructor ctor = (ErrorConstructor) realm.getIntrinsic(Intrinsics.Error);
        Object error = ctor.construct(realm.defaultContext(), Objects.toString(message, ""));
        return _throw(error);
    }

    private Path absolutePath(Path file) {
        return basedir.resolve(file);
    }

    private Path relativePath(Path file) {
        return basedir.resolve(script.getParent().resolve(file));
    }

    private String read(Path path) {
        if (!Files.exists(path)) {
            _throw(String.format("can't open '%s'", path.toString()));
        }
        try {
            byte[] bytes = Files.readAllBytes(path);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw throwError(realm, e.getMessage());
        }
    }

    private Object load(Path fileName, Path path) {
        if (!Files.exists(path)) {
            _throw(String.format("can't open '%s'", path.toString()));
        }
        try {
            eval(fileName, path);
            return UNDEFINED;
        } catch (IOException e) {
            throw throwError(realm, e.getMessage());
        } catch (ParserException e) {
            ExecutionContext cx = realm.defaultContext();
            if (e.getExceptionType() == ExceptionType.ReferenceError) {
                throw throwReferenceError(cx, e.getMessageKey(), e.getMessageArguments());
            }
            throw throwSyntaxError(cx, e.getMessageKey(), e.getMessageArguments());
        }
    }

    private Object evaluate(Realm realm, String source, String sourceName, int sourceLine)
            throws IOException {
        try {
            Script script = scriptCache.script(sourceName, sourceLine, new StringReader(source));
            return ScriptLoader.ScriptEvaluation(script, realm, false);
        } catch (ParserException e) {
            ExecutionContext cx = realm.defaultContext();
            if (e.getExceptionType() == ExceptionType.ReferenceError) {
                throw throwReferenceError(cx, e.getMessageKey(), e.getMessageArguments());
            }
            throw throwSyntaxError(cx, e.getMessageKey(), e.getMessageArguments());
        }
    }

    /**
     * Returns the well-known symbol {@code name} or undefined if there is no such symbol
     */
    @Function(name = "getSym", arity = 1)
    public Object getSym(String name) {
        try {
            if (name.startsWith("@@")) {
                return BuiltinSymbol.valueOf(name.substring(2)).get();
            }
        } catch (IllegalArgumentException e) {
        }
        return UNDEFINED;
    }

    /**
     * Creates a new Symbol object
     */
    @Function(name = "newSym", arity = 2)
    public Object newSym(String name, boolean _private) {
        return new ExoticSymbolObject(name, _private);
    }

    /**
     * {@code $INCLUDE} function to load scripts from library directory
     */
    @Function(name = "__$INCLUDE", arity = 1)
    public void $INCLUDE(String file) {
        try {
            // resolve the input file against the lib-path
            include(libdir.resolve(file));
        } catch (IOException e) {
            throw throwError(realm, e.getMessage());
        } catch (ParserException e) {
            ExecutionContext cx = realm.defaultContext();
            if (e.getExceptionType() == ExceptionType.ReferenceError) {
                throw throwReferenceError(cx, e.getMessageKey(), e.getMessageArguments());
            }
            throw throwSyntaxError(cx, e.getMessageKey(), e.getMessageArguments());
        }
    }

    /** shell-function: {@code version([number])} */
    @Function(name = "version", arity = 1)
    public String version() {
        return "185";
    }

    /** shell-function: {@code options([name])} */
    @Function(name = "options", arity = 0)
    public String options() {
        return "";
    }

    /** shell-function: {@code load(filename)} */
    @Function(name = "load", arity = 1)
    public Object load(String filename) {
        return load(Paths.get(filename), absolutePath(Paths.get(filename)));
    }

    /** shell-function: {@code loadRelativeToScript(filename)} */
    @Function(name = "loadRelativeToScript", arity = 1)
    public Object loadRelativeToScript(String filename) {
        return load(Paths.get(filename), relativePath(Paths.get(filename)));
    }

    /** shell-function: {@code evaluate(code, [options])} */
    @Function(name = "evaluate", arity = 2)
    public Object evaluate(Object code, Object options) {
        if (!(Type.isString(code) && (Type.isUndefined(options) || Type.isObject(options)))) {
            throwError(realm, "invalid arguments");
        }

        String source = Type.stringValue(code).toString();
        String sourceName = "@evaluate";
        int sourceLine = 1;
        boolean noScriptRval = false;
        boolean catchTermination = false;
        ScriptObject global = realm.getGlobalThis();
        if (Type.isObject(options)) {
            ExecutionContext cx = realm.defaultContext();
            ScriptObject opts = Type.objectValue(options);

            Object fileName = opts.get(cx, "fileName", opts);
            if (!Type.isUndefined(fileName)) {
                sourceName = Type.isNull(fileName) ? "" : ToFlatString(cx, fileName);
            }
            Object lineNumber = opts.get(cx, "lineNumber", opts);
            if (!Type.isUndefined(lineNumber)) {
                sourceLine = ToInt32(cx, lineNumber);
            }
            Object g = opts.get(cx, "global", opts);
            if (!Type.isUndefined(g)) {
                global = ToObject(cx, g);
                if (!(global instanceof GlobalObject)) {
                    throwError(realm, "invalid global argument");
                }
            }
            noScriptRval = ToBoolean(opts.get(cx, "noScriptRval", opts));
            catchTermination = ToBoolean(opts.get(cx, "catchTermination", opts));
        }

        try {
            Object result = evaluate(global.getRealm(), source, sourceName, sourceLine);
            return (!noScriptRval ? result : UNDEFINED);
        } catch (IOException | Error e) {
            if (catchTermination) {
                return "terminated";
            }
            throw throwError(realm, e.getMessage());
        }
    }

    /** shell-function: {@code run(file)} */
    @Function(name = "run", arity = 1)
    public double run(String file) {
        long start = System.nanoTime();
        load(file);
        long end = System.nanoTime();
        return (double) TimeUnit.NANOSECONDS.toMillis(end - start);
    }

    /** shell-function: {@code readline()} */
    @Function(name = "readline", arity = 0)
    public String readline() {
        return console.readLine();
    }

    /** shell-function: {@code print(message)} */
    @Function(name = "print", arity = 1)
    public void print(String message) {
        console.print(message);
    }

    /** shell-function: {@code printErr(message)} */
    @Function(name = "printErr", arity = 1)
    public void printErr(String message) {
        console.printErr(message);
    }

    /** shell-function: {@code putstr(message)} */
    @Function(name = "putstr", arity = 1)
    public void putstr(String message) {
        console.putstr(message);
    }

    /** shell-function: {@code dateNow()} */
    @Function(name = "dateNow", arity = 0)
    public double dateNow() {
        long elapsed = TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - startNano);
        double date = startMilli + TimeUnit.MICROSECONDS.toMillis(elapsed);
        double subdate = (elapsed % 1000) / 1000d;
        return date + subdate;
    }

    /** shell-function: {@code quit()} */
    @Function(name = "quit", arity = 0)
    public void quit() {
        throw new StopExecutionException(StopExecutionException.Reason.Quit);
    }

    /** shell-function: {@code assertEq()} */
    @Function(name = "assertEq", arity = 2)
    public void assertEq(Object actual, Object expected, Object message) {
        if (!SameValue(actual, expected)) {
            ExecutionContext cx = realm.defaultContext();
            StringBuilder msg = new StringBuilder();
            msg.append(String.format("Assertion failed: got %s, expected %s", ToSource(cx, actual),
                    ToSource(cx, expected)));
            if (!Type.isUndefined(message)) {
                msg.append(": ").append(ToFlatString(cx, message));
            }
            throwError(realm, msg.toString());
        }
    }

    /** shell-function: {@code throwError()} */
    @Function(name = "throwError", arity = 0)
    public void throwError() {
        throwError(realm, "This is an error");
    }

    /** shell-function: {@code build()} */
    @Function(name = "build", arity = 0)
    public String build() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                MozShellGlobalObject.class.getResourceAsStream("/build-date"),
                StandardCharsets.UTF_8))) {
            return reader.readLine();
        } catch (IOException e) {
            throw throwError(realm, "could not read build-date file");
        }
    }

    /** shell-function: {@code evalcx(s, [o])} */
    @Function(name = "evalcx", arity = 1)
    public Object evalcx(String s, Object o) {
        ScriptObject global;
        if (Type.isUndefinedOrNull(o)) {
            global = newGlobal();
        } else {
            global = ToObject(realm.defaultContext(), o);
        }
        if (s.isEmpty() || "lazy".equals(s)) {
            return global;
        }
        if (!(global instanceof GlobalObject)) {
            throwError(realm, "invalid global argument");
        }
        try {
            return evaluate(global.getRealm(), s, "evalcx", 1);
        } catch (IOException e) {
            throw throwError(realm, e.getMessage());
        }
    }

    /** shell-function: {@code sleep(dt)} */
    @Function(name = "sleep", arity = 1)
    public void sleep(double dt) {
        try {
            TimeUnit.SECONDS.sleep(ToUint32(dt));
        } catch (InterruptedException e) {
            throwError(realm, e.getMessage());
        }
    }

    /** shell-function: {@code snarf(filename)} */
    @Function(name = "snarf", arity = 1)
    public Object snarf(String filename) {
        return read(filename);
    }

    /** shell-function: {@code read(filename)} */
    @Function(name = "read", arity = 1)
    public Object read(String filename) {
        return read(absolutePath(Paths.get(filename)));
    }

    /** shell-function: {@code readRelativeToScript(filename)} */
    @Function(name = "readRelativeToScript", arity = 1)
    public Object readRelativeToScript(String filename) {
        return read(relativePath(Paths.get(filename)));
    }

    /** shell-function: {@code elapsed()} */
    @Function(name = "elapsed", arity = 0)
    public double elapsed() {
        return (double) TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - startNano);
    }

    /** shell-function: {@code decompileFunction(function)} */
    @Function(name = "decompileFunction", arity = 1)
    public Object decompileFunction(Object function) {
        if (!(function instanceof Callable)) {
            return UNDEFINED;
        }
        return ((Callable) function).toSource();
    }

    /** shell-function: {@code decompileBody(function)} */
    @Function(name = "decompileBody", arity = 1)
    public Object decompileBody(Object function) {
        if (!(function instanceof Callable)) {
            return UNDEFINED;
        }
        String source = ((Callable) function).toSource();
        Parser parser = new Parser("<decompileBody>", 1);
        com.github.anba.es6draft.ast.Script parsedScript = parser.parse("(" + source + ")");
        ExpressionStatement expr = (ExpressionStatement) parsedScript.getStatements().get(0);
        FunctionNode fnode = (FunctionNode) expr.getExpression();
        return fnode.getBodySource();
    }

    /** shell-function: {@code wrap(obj)} */
    @Function(name = "wrap", arity = 1)
    public Object wrap(Object obj) {
        if (!Type.isObject(obj)) {
            return obj;
        }
        return CreateWrapProxy(realm.defaultContext(), obj);
    }

    /** shell-function: {@code wrapWithProto(obj, proto)} */
    @Function(name = "wrapWithProto", arity = 2)
    public Object wrapWithProto(Object obj, Object proto) {
        return CreateWrapProxy(realm.defaultContext(), obj, proto);
    }

    /** shell-function: {@code newGlobal()} */
    @Function(name = "newGlobal", arity = 0)
    public ScriptObject newGlobal() {
        return newGlobal(console, basedir, script, libdir, scriptCache, initScript);
    }

    /** shell-function: {@code getMaxArgs()} */
    @Function(name = "getMaxArgs", arity = 0)
    public double getMaxArgs() {
        return FunctionPrototype.getMaxArguments();
    }

    /** shell-function: {@code gc()} */
    @Function(name = "gc", arity = 0)
    public String gc() {
        return "";
    }

    /** shell-function: {@code gczeal()} */
    @Function(name = "gczeal", arity = 0)
    public String gczeal() {
        return "";
    }

    /** shell-function: {@code isProxy(p)} */
    @Function(name = "isProxy", arity = 1)
    public boolean isProxy(Object p) {
        return (p instanceof ExoticProxy || p instanceof WrapperProxy);
    }

    /** shell-function: {@code terminate()} */
    @Function(name = "terminate", arity = 0)
    public void terminate() {
        throw new StopExecutionException(StopExecutionException.Reason.Terminate);
    }
}