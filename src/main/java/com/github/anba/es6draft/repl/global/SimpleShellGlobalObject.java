/**
 * Copyright (c) 2012-2015 André Bargull
 * Alle Rechte vorbehalten / All Rights Reserved.  Use is subject to license terms.
 *
 * <https://github.com/anba/es6draft>
 */
package com.github.anba.es6draft.repl.global;

import static com.github.anba.es6draft.runtime.AbstractOperations.Get;
import static com.github.anba.es6draft.runtime.AbstractOperations.ToBoolean;
import static com.github.anba.es6draft.runtime.AbstractOperations.ToFlatString;
import static com.github.anba.es6draft.runtime.AbstractOperations.ToInt32;
import static com.github.anba.es6draft.runtime.modules.ModuleSemantics.GetModuleNamespace;
import static com.github.anba.es6draft.runtime.modules.SourceTextModuleRecord.ParseModule;
import static com.github.anba.es6draft.runtime.objects.binary.ArrayBufferConstructor.DetachArrayBuffer;
import static com.github.anba.es6draft.runtime.types.Undefined.UNDEFINED;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

import com.github.anba.es6draft.Executable;
import com.github.anba.es6draft.Script;
import com.github.anba.es6draft.compiler.CompilationException;
import com.github.anba.es6draft.parser.ParserException;
import com.github.anba.es6draft.repl.console.ShellConsole;
import com.github.anba.es6draft.runtime.ExecutionContext;
import com.github.anba.es6draft.runtime.Realm;
import com.github.anba.es6draft.runtime.Task;
import com.github.anba.es6draft.runtime.internal.DebugInfo;
import com.github.anba.es6draft.runtime.internal.Errors;
import com.github.anba.es6draft.runtime.internal.Messages;
import com.github.anba.es6draft.runtime.internal.ObjectAllocator;
import com.github.anba.es6draft.runtime.internal.Properties.Function;
import com.github.anba.es6draft.runtime.internal.ScriptCache;
import com.github.anba.es6draft.runtime.internal.Source;
import com.github.anba.es6draft.runtime.modules.MalformedNameException;
import com.github.anba.es6draft.runtime.modules.ModuleRecord;
import com.github.anba.es6draft.runtime.modules.ModuleSource;
import com.github.anba.es6draft.runtime.modules.ResolutionException;
import com.github.anba.es6draft.runtime.modules.SourceIdentifier;
import com.github.anba.es6draft.runtime.modules.SourceTextModuleRecord;
import com.github.anba.es6draft.runtime.modules.loader.StringModuleSource;
import com.github.anba.es6draft.runtime.objects.ErrorObject;
import com.github.anba.es6draft.runtime.objects.GlobalObject;
import com.github.anba.es6draft.runtime.objects.binary.ArrayBufferObject;
import com.github.anba.es6draft.runtime.objects.collection.WeakMapObject;
import com.github.anba.es6draft.runtime.objects.reflect.RealmObject;
import com.github.anba.es6draft.runtime.types.Callable;
import com.github.anba.es6draft.runtime.types.ScriptObject;
import com.github.anba.es6draft.runtime.types.Type;
import com.github.anba.es6draft.runtime.types.builtins.FunctionObject;

/**
 *
 */
public class SimpleShellGlobalObject extends ShellGlobalObject {
    public SimpleShellGlobalObject(Realm realm, ShellConsole console, Path baseDir, Path script,
            ScriptCache scriptCache) {
        super(realm, console, baseDir, script, scriptCache);
    }

    @Override
    protected void initializeExtensions() {
        super.initializeExtensions();
        install(this, SimpleShellGlobalObject.class);
    }

    /**
     * Returns an object to allocate new instances of this class.
     * 
     * @param console
     *            the console object
     * @param baseDir
     *            the base directory
     * @param script
     *            the main script file
     * @param scriptCache
     *            the script cache
     * @return the object allocator to construct new global object instances
     */
    public static ObjectAllocator<SimpleShellGlobalObject> newGlobalObjectAllocator(
            final ShellConsole console, final Path baseDir, final Path script,
            final ScriptCache scriptCache) {
        return new ObjectAllocator<SimpleShellGlobalObject>() {
            @Override
            public SimpleShellGlobalObject newInstance(Realm realm) {
                return new SimpleShellGlobalObject(realm, console, baseDir, script, scriptCache);
            }
        };
    }

    /**
     * shell-function: {@code parseModule(source)}
     *
     * @param cx
     *            the execution context
     * @param source
     *            the source string to compile
     * @return the status message
     */
    @Function(name = "parseModule", arity = 1)
    public String parseModule(ExecutionContext cx, String source) {
        try {
            cx.getRealm().getScriptLoader().parseModule(new Source("<module>", 1), source);
        } catch (ParserException e) {
            throw e.toScriptException(cx);
        }
        return "success";
    }

    /**
     * shell-function: {@code parseScript(source)}
     * 
     * @param cx
     *            the execution context
     * @param source
     *            the source string to compile
     * @return the status message
     */
    @Function(name = "parseScript", arity = 1)
    public String parseScript(ExecutionContext cx, String source) {
        try {
            cx.getRealm().getScriptLoader().parseScript(new Source("<script>", 1), source);
        } catch (ParserException e) {
            throw e.toScriptException(cx);
        }
        return "success";
    }

    /**
     * shell-function: {@code compile(filename)}
     *
     * @param cx
     *            the execution context
     * @param filename
     *            the file to load
     * @return the status message
     */
    @Function(name = "compile", arity = 1)
    public String compile(ExecutionContext cx, String filename) {
        try {
            Path file = absolutePath(Paths.get(filename));
            getScriptLoader().script(new Source(file, filename, 1), file);
        } catch (ParserException | CompilationException | IOException e) {
            return "error: " + e.getMessage();
        }
        return "success";
    }

    /**
     * shell-function: {@code loadRelativeToScript(filename)}
     * 
     * @param cx
     *            the execution context
     * @param caller
     *            the caller execution context
     * @param filename
     *            the file to load
     * @return the result value
     */
    @Function(name = "loadRelativeToScript", arity = 1)
    public Object loadRelativeToScript(ExecutionContext cx, ExecutionContext caller, String filename) {
        return load(cx, Paths.get(filename), relativePathToScript(caller, Paths.get(filename)));
    }

    /**
     * shell-function: {@code readRelativeToScript(filename)}
     * 
     * @param cx
     *            the execution context
     * @param caller
     *            the caller execution context
     * @param filename
     *            the file to load
     * @return the result value
     */
    @Function(name = "readRelativeToScript", arity = 1)
    public Object readRelativeToScript(ExecutionContext cx, ExecutionContext caller, String filename) {
        return read(cx, Paths.get(filename), relativePathToScript(caller, Paths.get(filename)));
    }

    /**
     * shell-function: {@code loadModule(moduleName, [realmObject])}
     * 
     * @param cx
     *            the execution context
     * @param moduleName
     *            the module name
     * @param realmObject
     *            the optional realm object
     * @return the result value
     */
    @Function(name = "loadModule", arity = 1)
    public ScriptObject loadModule(ExecutionContext cx, String moduleName, Object realmObject) {
        Realm realm;
        if (!Type.isUndefined(realmObject)) {
            if (!(realmObject instanceof RealmObject)) {
                throw Errors.newTypeError(cx, Messages.Key.IncompatibleObject);
            }
            realm = ((RealmObject) realmObject).getRealm();
            if (realm == null) {
                throw Errors.newTypeError(cx, Messages.Key.UninitializedObject);
            }
        } else {
            realm = cx.getRealm();
        }
        try {
            ModuleRecord module = eval(realm, moduleName);
            return GetModuleNamespace(cx, module);
        } catch (MalformedNameException | ResolutionException e) {
            throw e.toScriptException(cx);
        } catch (ParserException | CompilationException e) {
            throw e.toScriptException(cx);
        } catch (IOException e) {
            throw Errors.newInternalError(cx, e, Messages.Key.ModulesIOException, e.getMessage());
        }
    }

    /**
     * shell-function: {@code dump(object)}
     * 
     * @param object
     *            the object to inspect
     */
    @Function(name = "dump", arity = 1)
    public void dump(Object object) {
        String id;
        if (!Type.isType(object)) {
            // foreign object or null
            id = Objects.toString(object, "<null>");
        } else if (!Type.isObject(object)) {
            // primitive value
            id = String.format("%s (%s)", object.toString(), object.getClass().getSimpleName());
        } else {
            // script objects
            id = String.format("%s@%x", object.getClass().getSimpleName(),
                    System.identityHashCode(object));
        }
        console.print(id);
    }

    /**
     * shell-function: {@code dumpObject(object)}
     * 
     * @param object
     *            the object to inspect
     */
    @Function(name = "dumpObject", arity = 1)
    public void dumpObject(ScriptObject object) {
        console.print(object.toString());
    }

    /**
     * shell-function: {@code dumpScope()}
     * 
     * @param cx
     *            the execution context
     * @param caller
     *            the caller context
     */
    @Function(name = "dumpScope", arity = 0)
    public void dumpScope(ExecutionContext cx, ExecutionContext caller) {
        if (caller.getLexicalEnvironment() != null) {
            console.print(caller.getLexicalEnvironment().toString());
        }
    }

    /**
     * shell-function: {@code dumpSymbolRegistry()}
     * 
     * @param cx
     *            the execution context
     */
    @Function(name = "dumpSymbolRegistry", arity = 0)
    public void dumpSymbolRegistry(ExecutionContext cx) {
        console.print(cx.getRealm().getSymbolRegistry().toString());
    }

    /**
     * shell-function: {@code dumpTemplateMap()}
     * 
     * @param cx
     *            the execution context
     */
    @Function(name = "dumpTemplateMap", arity = 0)
    public void dumpTemplateMap(ExecutionContext cx) {
        console.print(cx.getRealm().getTemplateMap().toString());
    }

    /**
     * shell-function: {@code gc()}
     */
    @Function(name = "gc", arity = 0)
    public void gc() {
        System.gc();
    }

    /**
     * shell-function: {@code error()}
     */
    @Function(name = "error", arity = 0)
    public void error() {
        throw new AssertionError();
    }

    /**
     * shell-function: {@code printStackTrace(object)}
     * 
     * @param object
     *            the error object
     */
    @Function(name = "printStackTrace", arity = 1)
    public void printStackTrace(ErrorObject object) {
        object.getException().printStackTrace();
    }

    /**
     * shell-function: {@code putstr(message)}
     *
     * @param message
     *            the message to write
     */
    @Function(name = "putstr", arity = 1)
    public void putstr(String message) {
        console.putstr(message);
    }

    /**
     * shell-function: {@code nextTick(function)}
     * 
     * @param cx
     *            the execution context
     * @param function
     *            the callback function
     */
    @Function(name = "nextTick", arity = 1)
    public void nextTick(final ExecutionContext cx, final Callable function) {
        cx.getRealm().enqueuePromiseTask(new Task() {
            @Override
            public void execute() {
                function.call(cx, UNDEFINED);
            }
        });
    }

    /**
     * shell-function: {@code detachArrayBuffer(arrayBuffer)}
     * 
     * @param cx
     *            the execution context
     * @param arrayBuffer
     *            the array buffer object
     */
    @Function(name = "detachArrayBuffer", arity = 1)
    public void detachArrayBuffer(ExecutionContext cx, ArrayBufferObject arrayBuffer) {
        // initialized and not already detached
        if (arrayBuffer.getData() != null) {
            DetachArrayBuffer(cx, arrayBuffer);
        }
    }

    /**
     * shell-function: {@code weakMapSize(weakMap)}
     * 
     * @param weakMap
     *            the WeakMap object
     * @return the WeakMap's current size
     */
    @Function(name = "weakMapSize", arity = 1)
    public int weakMapSize(WeakMapObject weakMap) {
        return weakMap.getWeakMapData().size();
    }

    /**
     * shell-function: {@code version()}
     *
     * @return the version string
     */
    @Function(name = "version", arity = 1)
    public String version() {
        return String.format("%s", getResourceInfo("/version", "<unknown version>"));
    }

    /**
     * shell-function: {@code disassemble([function])}
     * 
     * @param cx
     *            the execution context
     * @param caller
     *            the caller context
     * @param args
     *            the arguments
     * @throws IOException
     *             if there was any I/O error
     * @throws MalformedNameException
     *             if the module name cannot be normalized
     */
    @Function(name = "disassemble", arity = 1)
    public void disassemble(ExecutionContext cx, ExecutionContext caller, Object... args)
            throws IOException, MalformedNameException {
        DebugInfo debugInfo = null;
        if (args.length == 0) {
            FunctionObject currentFunction = caller.getCurrentFunction();
            Executable currentExec = caller.getCurrentExecutable();
            if (currentFunction != null && currentFunction.getExecutable() == currentExec) {
                debugInfo = currentFunction.getCode().debugInfo();
            } else if (currentExec != null && currentExec.getSourceObject() != null) {
                debugInfo = currentExec.getSourceObject().debugInfo();
            }
        } else if (args[0] instanceof FunctionObject) {
            debugInfo = ((FunctionObject) args[0]).getCode().debugInfo();
        } else {
            String sourceCode = ToFlatString(cx, args[0]);
            boolean isModule = false;
            if (args.length > 1 && Type.isObject(args[1])) {
                isModule = ToBoolean(Get(cx, Type.objectValue(args[1]), "module"));
            }
            if (isModule) {
                SourceIdentifier identifier = getRealm().getModuleLoader().normalizeName(
                        "disassemble", null);
                ModuleSource src = new StringModuleSource(identifier, sourceCode);
                SourceTextModuleRecord module = ParseModule(getScriptLoader(), identifier, src);
                debugInfo = module.getScriptCode().getSourceObject().debugInfo();
            } else {
                Source source = new Source("<disassemble>", 1);
                Script script = getScriptLoader().compile(
                        getScriptLoader().parseScript(source, sourceCode), "#disassemble");
                debugInfo = script.getSourceObject().debugInfo();
            }
        }
        if (debugInfo != null) {
            for (DebugInfo.Method method : debugInfo.getMethods()) {
                console.print(method.disassemble());
            }
        }
    }

    /**
     * shell-function: {@code evalScript(sourceString, [options])}
     * 
     * @param cx
     *            the execution context
     * @param sourceString
     *            the source string
     * @param options
     *            the options object (optional)
     * @return the evaluation result
     */
    @Function(name = "evalScript", arity = 1)
    public Object evalScript(ExecutionContext cx, String sourceString, Object options) {
        String name = "";
        int line = 1;
        Realm realm = getRealm();
        if (Type.isObject(options)) {
            ScriptObject opts = Type.objectValue(options);
            Object fileName = Get(cx, opts, "fileName");
            if (!Type.isUndefined(fileName)) {
                name = ToFlatString(cx, fileName);
            }
            Object lineNumber = Get(cx, opts, "lineNumber");
            if (!Type.isUndefined(lineNumber)) {
                line = ToInt32(cx, lineNumber);
            }
            Object g = Get(cx, opts, "global");
            if (!Type.isUndefined(g)) {
                if (!(g instanceof GlobalObject)) {
                    throw newError(cx, "invalid global argument");
                }
                realm = ((GlobalObject) g).getRealm();
            }
            Object r = Get(cx, opts, "realm");
            if (!Type.isUndefined(r)) {
                if (!(r instanceof RealmObject)) {
                    throw newError(cx, "invalid realm argument");
                }
                realm = ((RealmObject) r).getRealm();
            }
        }
        Source source = new Source(name, line);
        Script script = getScriptLoader().script(source, sourceString);
        return eval(script, realm);
    }
}
