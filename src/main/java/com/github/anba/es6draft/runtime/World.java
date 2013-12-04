/**
 * Copyright (c) 2012-2013 André Bargull
 * Alle Rechte vorbehalten / All Rights Reserved.  Use is subject to license terms.
 *
 * <https://github.com/anba/es6draft>
 */
package com.github.anba.es6draft.runtime;

import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

import com.github.anba.es6draft.compiler.Compiler;
import com.github.anba.es6draft.compiler.Compiler.Option;
import com.github.anba.es6draft.runtime.internal.CompatibilityOption;
import com.github.anba.es6draft.runtime.internal.Messages;
import com.github.anba.es6draft.runtime.internal.Microtask;
import com.github.anba.es6draft.runtime.internal.ObjectAllocator;
import com.github.anba.es6draft.runtime.objects.GlobalObject;

/**
 * <h1>8 Executable Code and Execution Contexts</h1>
 */
public final class World {
    private final EnumSet<CompatibilityOption> options;
    private final EnumSet<Option> compilerOptions;

    // TODO: move to custom class
    private ArrayDeque<Microtask> tasks = new ArrayDeque<>();

    private Locale locale = Locale.getDefault();
    private TimeZone timezone = TimeZone.getDefault();
    private Messages messages = Messages.create(locale);

    /**
     * Creates a new {@link World} object with the default settings
     */
    public World() {
        this(CompatibilityOption.WebCompatibility(), EnumSet.noneOf(Compiler.Option.class));
    }

    /**
     * Creates a new {@link World} object
     */
    public World(Set<CompatibilityOption> options) {
        this(options, EnumSet.noneOf(Compiler.Option.class));
    }

    /**
     * Creates a new {@link World} object
     */
    public World(Set<CompatibilityOption> options, Set<Compiler.Option> compilerOptions) {
        this.options = EnumSet.copyOf(options);
        this.compilerOptions = EnumSet.copyOf(compilerOptions);
    }

    /**
     * Checks whether there are any pending micro-tasks
     */
    public boolean hasTasks() {
        return !tasks.isEmpty();
    }

    /**
     * Enqueues {@code task} to the queue of pending micro-tasks
     */
    public void enqueueTask(Microtask task) {
        tasks.offer(task);
    }

    /**
     * Executes the queue of pending micro-tasks
     */
    public void executeTasks(ExecutionContext cx) {
        ArrayDeque<Microtask> tasks = this.tasks;
        // execute all pending micro-tasks until the queue is empty
        for (Microtask task; (task = tasks.poll()) != null;) {
            // TODO: ignore exceptions or stop execution?
            task.execute(cx);
        }
    }

    /**
     * Returns this world's locale
     */
    public Locale getLocale() {
        return locale;
    }

    /**
     * Returns this world's timezone
     */
    public TimeZone getTimezone() {
        return timezone;
    }

    /**
     * Returns the localised message for {@code key}
     */
    public String message(Messages.Key key) {
        return messages.getString(key);
    }

    /**
     * Returns the compatibility options for this instance
     */
    public Set<CompatibilityOption> getOptions() {
        return options;
    }

    /**
     * Tests whether the requested compatibility option is enabled for this instance
     */
    public boolean isEnabled(CompatibilityOption option) {
        return options.contains(option);
    }

    /**
     * Returns the compiler options for this instance
     */
    public EnumSet<Option> getCompilerOptions() {
        return compilerOptions;
    }

    private static final ObjectAllocator<GlobalObject> DEFAULT_GLOBAL_OBJECT = new ObjectAllocator<GlobalObject>() {
        @Override
        public GlobalObject newInstance(Realm realm) {
            return new GlobalObject(realm);
        }
    };

    /**
     * Creates a new {@link Realm} object with the default settings
     */
    public Realm newRealm() {
        return newRealm(DEFAULT_GLOBAL_OBJECT);
    }

    /**
     * Creates a new {@link Realm} object
     */
    public Realm newRealm(ObjectAllocator<? extends GlobalObject> allocator) {
        return Realm.newRealm(this, allocator);
    }
}