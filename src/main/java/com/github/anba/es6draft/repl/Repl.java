/**
 * Copyright (c) 2012-2014 André Bargull
 * Alle Rechte vorbehalten / All Rights Reserved.  Use is subject to license terms.
 *
 * <https://github.com/anba/es6draft>
 */
package com.github.anba.es6draft.repl;

import static com.github.anba.es6draft.repl.SourceBuilder.ToSource;
import static com.github.anba.es6draft.runtime.AbstractOperations.CreateArrayFromList;
import static com.github.anba.es6draft.runtime.types.Undefined.UNDEFINED;

import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Formatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jline.TerminalFactory;
import jline.TerminalSupport;
import jline.UnsupportedTerminal;
import jline.console.ConsoleReader;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.OptionHandlerFilter;
import org.kohsuke.args4j.spi.OptionHandler;
import org.kohsuke.args4j.spi.Parameters;
import org.kohsuke.args4j.spi.RestOfArgumentsHandler;
import org.kohsuke.args4j.spi.Setter;
import org.kohsuke.args4j.spi.StopOptionHandler;

import com.github.anba.es6draft.Script;
import com.github.anba.es6draft.ScriptLoader;
import com.github.anba.es6draft.compiler.CompilationException;
import com.github.anba.es6draft.compiler.Compiler;
import com.github.anba.es6draft.parser.Parser;
import com.github.anba.es6draft.parser.ParserEOFException;
import com.github.anba.es6draft.parser.ParserException;
import com.github.anba.es6draft.repl.console.JLineConsole;
import com.github.anba.es6draft.repl.console.LegacyConsole;
import com.github.anba.es6draft.repl.console.NativeConsole;
import com.github.anba.es6draft.repl.console.ReplConsole;
import com.github.anba.es6draft.repl.global.MozShellGlobalObject;
import com.github.anba.es6draft.repl.global.ShellGlobalObject;
import com.github.anba.es6draft.repl.global.SimpleShellGlobalObject;
import com.github.anba.es6draft.repl.global.StopExecutionException;
import com.github.anba.es6draft.repl.global.StopExecutionException.Reason;
import com.github.anba.es6draft.repl.global.V8ShellGlobalObject;
import com.github.anba.es6draft.runtime.ExecutionContext;
import com.github.anba.es6draft.runtime.Realm;
import com.github.anba.es6draft.runtime.Task;
import com.github.anba.es6draft.runtime.World;
import com.github.anba.es6draft.runtime.internal.CompatibilityOption;
import com.github.anba.es6draft.runtime.internal.ObjectAllocator;
import com.github.anba.es6draft.runtime.internal.ScriptCache;
import com.github.anba.es6draft.runtime.internal.ScriptException;
import com.github.anba.es6draft.runtime.internal.Strings;
import com.github.anba.es6draft.runtime.types.PropertyDescriptor;
import com.github.anba.es6draft.runtime.types.ScriptObject;

/**
 * Simple REPL
 */
public final class Repl {
    private static final String PROGRAM_NAME = "es6draft";
    private static final int STACKTRACE_DEPTH = 20;

    public static void main(String[] args) throws Throwable {
        try {
            Options options = readOptions(args);
            ReplConsole console = createConsole(options);
            new Repl(console, options).loop();
        } catch (Throwable e) {
            printStackTrace(e);
            System.exit(1);
        }
    }

    private static ReplConsole createConsole(Options options) throws IOException {
        ReplConsole console;
        if (!options.noJLine) {
            configureTerminalFlavors();
            ConsoleReader consoleReader = new ConsoleReader(PROGRAM_NAME, new FileInputStream(
                    FileDescriptor.in), System.out, TerminalFactory.get(), getDefaultEncoding());
            consoleReader.setExpandEvents(false);
            console = new JLineConsole(consoleReader);
        } else if (System.console() != null) {
            console = new NativeConsole(System.console());
        } else {
            console = new LegacyConsole(System.out, System.in);
        }
        return console;
    }

    private static void configureTerminalFlavors() {
        final boolean isWindows = isWindows();
        final String type = System.getProperty(TerminalFactory.JLINE_TERMINAL);
        if (isWindows && type == null) {
            TerminalFactory.registerFlavor(TerminalFactory.Flavor.WINDOWS,
                    UnsupportedTerminal.class);
        } else if (isWindows && type.equalsIgnoreCase(TerminalFactory.UNIX)) {
            TerminalFactory.registerFlavor(TerminalFactory.Flavor.UNIX, CygwinTerminal.class);
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").startsWith("Windows");
    }

    private static String getDefaultEncoding() {
        return Charset.defaultCharset().name();
    }

    public static final class CygwinTerminal extends TerminalSupport {
        private final int width, height;

        public CygwinTerminal() {
            super(true);
            String settings = System.getProperty(TerminalFactory.JLINE_TERMINAL + ".settings", "");
            width = getProperty(settings, "columns", DEFAULT_WIDTH);
            height = getProperty(settings, "rows", DEFAULT_HEIGHT);
        }

        private static int getProperty(String settings, String name, int defaultValue) {
            Matcher m = Pattern.compile(name + "\\s+(\\d{1,4})").matcher(settings);
            return m.find() ? Integer.parseInt(m.group(1)) : defaultValue;
        }

        @Override
        public void init() throws Exception {
            super.init();
            setEchoEnabled(false);
            setAnsiSupported(true);
        }

        @Override
        public int getWidth() {
            return width;
        }

        @Override
        public int getHeight() {
            return height;
        }
    }

    private static void printStackTrace(Throwable e) {
        StackTraceElement[] stackTrace = e.getStackTrace();
        if (stackTrace.length > STACKTRACE_DEPTH) {
            int omitted = stackTrace.length - STACKTRACE_DEPTH;
            stackTrace = Arrays.copyOf(stackTrace, STACKTRACE_DEPTH + 1);
            stackTrace[STACKTRACE_DEPTH] = new StackTraceElement("..", "", "Frames omitted",
                    omitted);
            e.setStackTrace(stackTrace);
        }
        e.printStackTrace();
    }

    private static Options readOptions(String[] args) {
        Options options = new Options();
        CmdLineParser parser = new CmdLineParser(options);
        parser.setUsageWidth(120);
        try {
            parser.parseArgument(args);
        } catch (CmdLineException e) {
            System.err.println(e.getMessage());
            System.err.println(getUsageString(parser));
            System.exit(1);
        }
        if (options.showVersion) {
            System.out.println(getVersionString());
            System.exit(0);
        }
        if (options.showHelp) {
            System.out.println(getUsageString(parser));
            System.exit(0);
        }
        if (options.debug || options.fullDebug) {
            // Disable interpreter when bytecode is requested
            options.noInterpreter = true;
        }
        if (options.fileName != null) {
            // Execute as last script
            options.evalScripts.add(new EvalPath(options.fileName));
        }
        if (options.evalScripts.isEmpty()) {
            // Default to interactive mode when no files or expressions were set
            options.interactive = true;
        }
        return options;
    }

    private static interface EvalScript {
        String getSourceName();

        String getSource() throws IOException;
    }

    private static final class EvalString implements EvalScript {
        private final String source;

        EvalString(String source) {
            this.source = source;
        }

        @Override
        public String getSourceName() {
            return "<eval>";
        }

        @Override
        public String getSource() {
            return source;
        }
    }

    private static final class EvalPath implements EvalScript {
        private final Path path;

        EvalPath(Path path) {
            this.path = path;
        }

        @Override
        public String getSourceName() {
            return path.toString();
        }

        @Override
        public String getSource() throws IOException {
            Path filePath = path.toAbsolutePath();
            if (!Files.exists(filePath)) {
                System.err.printf("File '%s' not found!%n", filePath);
                return null;
            }
            byte[] content = Files.readAllBytes(filePath);
            String source = new String(content, StandardCharsets.UTF_8);
            return source;
        }
    }

    public enum ShellMode {
        Simple, Mozilla, V8;

        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final class Options {
        List<EvalScript> evalScripts = new ArrayList<>();

        @Option(name = "-v", aliases = { "--version" },
                usage = "Print version information and exit")
        boolean showVersion;

        @Option(name = "-h", aliases = { "--help" }, usage = "Print this help")
        boolean showHelp;

        @Option(name = "-e", aliases = { "--eval", "--execute" }, metaVar = "STRING",
                usage = "Script code to evaluate")
        void setEvalExpression(String expression) {
            evalScripts.add(new EvalString(expression));
        }

        @Option(name = "-f", aliases = { "--file" }, metaVar = "FILE",
                usage = "Script file to evaluate")
        void setFile(Path path) {
            evalScripts.add(new EvalPath(path));
        }

        @Option(name = "-i", aliases = { "--interactive" }, usage = "Start interactive mode")
        boolean interactive;

        @Option(name = "--strict", usage = "Strict semantics without web compatibility")
        boolean strict;

        @Option(name = "--shell", usage = "Set default shell emulation (default = simple)")
        ShellMode shellMode = ShellMode.Simple;

        @Option(name = "--async", usage = "Enable experimental support for async functions")
        boolean asyncFunctions;

        @Option(name = "--no-jline", usage = "Disable JLine support")
        boolean noJLine;

        @Option(name = "--no-color", usage = "Disable colored output")
        boolean noColor;

        @Option(name = "--no-interpreter", aliases = { "--compile-only" },
                usage = "Disable interpreter")
        boolean noInterpreter;

        @Option(name = "--stacktrace", usage = "Print stack-trace on error")
        boolean stacktrace;

        @Option(name = "--debug", usage = "Print generated Java bytecode")
        boolean debug;

        @Option(name = "--full-debug", hidden = true,
                usage = "Print generated Java bytecode (full type descriptors)")
        boolean fullDebug;

        @Option(name = "--verify-stack", hidden = true,
                usage = "Enable stack usage tracking during compilation")
        boolean verifyStack;

        @Argument(index = 0, multiValued = false, metaVar = "FILE",
                usage = "Script file to execute")
        Path fileName = null;

        @Option(name = "--", handler = StopOptionAndConsumeRestHandler.class)
        @Argument(index = 1, multiValued = true, metaVar = "ARGUMENTS", usage = "Script arguments",
                handler = RestOfArgumentsHandler.class)
        List<String> arguments = new ArrayList<>();
    }

    public static final class StopOptionAndConsumeRestHandler extends OptionHandler<String> {
        private final StopOptionHandler stopOptionHandler;
        private final RestOfArgumentsHandler restOfArgumentsHandler;

        public StopOptionAndConsumeRestHandler(CmdLineParser parser, OptionDef option,
                Setter<String> setter) {
            super(parser, option, setter);
            this.stopOptionHandler = new StopOptionHandler(parser, option, setter);
            this.restOfArgumentsHandler = new RestOfArgumentsHandler(parser, option, setter);
        }

        @Override
        public int parseArguments(Parameters params) throws CmdLineException {
            return stopOptionHandler.parseArguments(params)
                    + restOfArgumentsHandler.parseArguments(params);
        }

        @Override
        public String getDefaultMetaVariable() {
            return "ARGUMENTS";
        }
    }

    private static String getVersionString() {
        return getResourceInfo("/version", PROGRAM_NAME);
    }

    private static String getUsageString(CmdLineParser parser) {
        try (Formatter formatter = new Formatter()) {
            formatter.format("%s%n%n", getVersionString());
            formatter.format("Usage:%n");
            formatter.format("%s [options] [FILE [ARGUMENTS]]%n%n", PROGRAM_NAME);
            formatter.format("Options:%n");
            StringWriter writer = new StringWriter();
            parser.printUsage(writer, null, OptionHandlerFilter.PUBLIC);
            formatter.out().append(writer.toString());
            return formatter.toString();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String getResourceInfo(String resourceName, String defaultValue) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                Repl.class.getResourceAsStream(resourceName), StandardCharsets.UTF_8))) {
            return reader.readLine();
        } catch (IOException e) {
            return defaultValue;
        }
    }

    @SuppressWarnings("serial")
    private static final class ParserExceptionWithSource extends RuntimeException {
        private final String source;

        ParserExceptionWithSource(ParserException e, String source) {
            super(e);
            this.source = source;
        }

        @Override
        public ParserException getCause() {
            return (ParserException) super.getCause();
        }

        public String getSource() {
            return source;
        }
    }

    private final ReplConsole console;
    private final Options options;
    private AtomicInteger scriptCounter = new AtomicInteger(0);

    private Repl(ReplConsole console, Options options) {
        this.console = console;
        this.options = options;
    }

    private void handleException(Throwable e) {
        String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        console.printf("%s%n", message);
        if (options.stacktrace) {
            printStackTrace(e);
        }
    }

    private void handleException(Realm realm, ScriptException e) {
        console.printf("uncaught exception: %s%n", e.getMessage(realm.defaultContext()));
        if (options.stacktrace) {
            printStackTrace(e);
        }
    }

    private void handleException(ParserExceptionWithSource exception, int lineOffset) {
        ParserException e = exception.getCause();
        String source = exception.getSource();

        String sourceInfo = String.format("%s:%d:%d", e.getFile(), e.getLine(), e.getColumn());
        int start = skipLines(source, e.getLine() - lineOffset);
        int end = nextLineTerminator(source, start);
        String offendingLine = source.substring(start, end);
        String marker = Strings.repeat('.', Math.max(e.getColumn() - 1, 0)) + '^';

        console.printf("%s %s: %s%n", sourceInfo, e.getType(), e.getFormattedMessage());
        console.printf("%s %s%n", sourceInfo, offendingLine);
        console.printf("%s %s%n", sourceInfo, marker);
        if (options.stacktrace) {
            printStackTrace(e);
        }
    }

    private static int skipLines(String s, int n) {
        int index = 0;
        for (int length = s.length(); n > 0; --n) {
            int lineEnd = nextLineTerminator(s, index);
            if (lineEnd + 1 < length && s.charAt(lineEnd) == '\r' && s.charAt(lineEnd + 1) == '\n') {
                index = lineEnd + 2;
            } else {
                index = lineEnd + 1;
            }
        }
        return index;
    }

    private static int nextLineTerminator(String s, int index) {
        for (int length = s.length(); index < length && !Strings.isLineTerminator(s.charAt(index)); ++index) {
        }
        return index;
    }

    private static com.github.anba.es6draft.ast.Script parse(Realm realm, String sourceName,
            String source, int line) {
        Parser parser = new Parser(sourceName, line, realm.getOptions());
        return parser.parseScript(source);
    }

    /**
     * REPL: Read
     * 
     * @param realm
     *            the realm instance
     * @param line
     *            the current line
     * @return the parsed script node
     */
    private com.github.anba.es6draft.ast.Script read(Realm realm, int line) {
        StringBuilder source = new StringBuilder();
        for (String prompt = "js> ";; prompt = "") {
            String s = console.readLine(prompt);
            if (s == null) {
                continue;
            }
            source.append(s).append('\n');
            try {
                return parse(realm, "typein", source.toString(), line);
            } catch (ParserEOFException e) {
                continue;
            } catch (ParserException e) {
                throw new ParserExceptionWithSource(e, source.toString());
            }
        }
    }

    /**
     * REPL: Eval
     * 
     * @param realm
     *            the realm instance
     * @param parsedScript
     *            the parsed script node
     * @return the evaluated script result
     */
    private Object eval(Realm realm, com.github.anba.es6draft.ast.Script parsedScript) {
        String className = "typein_" + scriptCounter.incrementAndGet();
        Script script;
        if (options.noInterpreter) {
            script = ScriptLoader.compile(realm, parsedScript, className);
        } else {
            script = ScriptLoader.load(realm, parsedScript, className);
        }
        return ScriptLoader.ScriptEvaluation(script, realm, false);
    }

    /**
     * REPL: Print
     * 
     * @param realm
     *            the realm instance
     * @param result
     *            the object to be printed
     */
    private void print(Realm realm, Object result) {
        if (result != UNDEFINED) {
            boolean color = console.isAnsiSupported() && !options.noColor;
            SourceBuilder.Mode mode = color ? SourceBuilder.Mode.Color : SourceBuilder.Mode.Simple;
            console.printf("%s%n", ToSource(mode, realm.defaultContext(), result));
        }
    }

    /**
     * REPL: Loop
     */
    private void loop() {
        Realm realm = newRealm();
        for (int line = 1;; line += 1) {
            drainTaskQueue(realm);
            if (!options.interactive) {
                break;
            }
            try {
                com.github.anba.es6draft.ast.Script parsedScript = read(realm, line);
                if (parsedScript.getStatements().isEmpty()) {
                    continue;
                }
                Object result = eval(realm, parsedScript);
                print(realm, result);
            } catch (StopExecutionException e) {
                if (e.getReason() == Reason.Quit) {
                    System.exit(0);
                }
            } catch (ParserExceptionWithSource e) {
                handleException(e, line);
            } catch (ScriptException e) {
                handleException(realm, e);
            } catch (ParserException | CompilationException | StackOverflowError e) {
                handleException(e);
            } catch (BootstrapMethodError | UncheckedIOException e) {
                handleException(e.getCause());
            }
        }
    }

    private void drainTaskQueue(Realm realm) {
        World<?> world = realm.getWorld();
        while (world.hasPendingTasks()) {
            try {
                world.executeTasks();
            } catch (StopExecutionException e) {
                if (e.getReason() == Reason.Quit) {
                    System.exit(0);
                }
            } catch (ParserExceptionWithSource e) {
                handleException(e, 1);
            } catch (ScriptException e) {
                handleException(realm, e);
            } catch (ParserException | CompilationException | StackOverflowError e) {
                handleException(e);
            } catch (BootstrapMethodError | UncheckedIOException e) {
                handleException(e.getCause());
            }
        }
    }

    private Realm newRealm() {
        ReplConsole console = this.console;
        Path baseDir = Paths.get("").toAbsolutePath();
        Path script = Paths.get("./.");
        Set<CompatibilityOption> compatibilityOptions;
        if (options.strict) {
            compatibilityOptions = CompatibilityOption.StrictCompatibility();
        } else if (options.shellMode == ShellMode.Mozilla) {
            compatibilityOptions = CompatibilityOption.MozCompatibility();
        } else {
            compatibilityOptions = CompatibilityOption.WebCompatibility();
        }
        if (options.asyncFunctions) {
            compatibilityOptions.add(CompatibilityOption.AsyncFunction);
        }
        Set<Parser.Option> parserOptions = EnumSet.noneOf(Parser.Option.class);
        Set<Compiler.Option> compilerOptions = EnumSet.noneOf(Compiler.Option.class);
        if (options.debug) {
            compilerOptions.add(Compiler.Option.Debug);
        }
        if (options.fullDebug) {
            compilerOptions.add(Compiler.Option.FullDebug);
        }
        if (options.verifyStack) {
            compilerOptions.add(Compiler.Option.VerifyStack);
        }
        ScriptCache scriptCache = new ScriptCache(compatibilityOptions, parserOptions,
                compilerOptions);

        ObjectAllocator<? extends ShellGlobalObject> allocator;
        if (options.shellMode == ShellMode.Mozilla) {
            allocator = MozShellGlobalObject.newGlobalObjectAllocator(console, baseDir, script,
                    scriptCache);
        } else if (options.shellMode == ShellMode.V8) {
            allocator = V8ShellGlobalObject.newGlobalObjectAllocator(console, baseDir, script,
                    scriptCache);
        } else {
            allocator = SimpleShellGlobalObject.newGlobalObjectAllocator(console, baseDir, script,
                    scriptCache);
        }

        World<? extends ShellGlobalObject> world = new World<>(allocator, compatibilityOptions,
                compilerOptions);
        final ShellGlobalObject global = world.newGlobal();
        final Realm realm = global.getRealm();
        ExecutionContext cx = realm.defaultContext();

        // Add completion to console
        console.addCompletion(realm);

        // Add global "arguments" property
        ScriptObject arguments = CreateArrayFromList(cx, options.arguments);
        global.defineOwnProperty(cx, "arguments", new PropertyDescriptor(arguments, true, false,
                true));

        // Execute any global specific initialisation scripts
        realm.enqueueLoadingTask(new Task() {
            @Override
            public void execute() {
                try {
                    global.initialize(global);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                } catch (URISyntaxException e) {
                    throw new UncheckedIOException(new IOException(e));
                }
            }
        });

        // Run eval expressions and files
        for (final EvalScript evalScript : options.evalScripts) {
            realm.enqueueLoadingTask(new Task() {
                @Override
                public void execute() {
                    try {
                        String sourceName = evalScript.getSourceName();
                        String source = evalScript.getSource();
                        if (source == null) {
                            // Return if source not available
                            return;
                        }
                        try {
                            eval(realm, parse(realm, sourceName, source, 1));
                        } catch (ParserException e) {
                            throw new ParserExceptionWithSource(e, source);
                        }
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            });
        }

        return realm;
    }
}
