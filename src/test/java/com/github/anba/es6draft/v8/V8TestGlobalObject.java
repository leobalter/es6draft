/**
 * Copyright (c) 2012-2015 André Bargull
 * Alle Rechte vorbehalten / All Rights Reserved.  Use is subject to license terms.
 *
 * <https://github.com/anba/es6draft>
 */
package com.github.anba.es6draft.v8;

import com.github.anba.es6draft.repl.console.ShellConsole;
import com.github.anba.es6draft.repl.global.V8ShellGlobalObject;
import com.github.anba.es6draft.runtime.Realm;
import com.github.anba.es6draft.runtime.internal.ObjectAllocator;
import com.github.anba.es6draft.runtime.internal.ScriptCache;
import com.github.anba.es6draft.util.TestInfo;

/**
 * 
 */
final class V8TestGlobalObject extends V8ShellGlobalObject {
    protected V8TestGlobalObject(Realm realm, ShellConsole console, TestInfo test,
            ScriptCache scriptCache) {
        super(realm, console, test.getBaseDir(), test.getScript(), scriptCache);
    }

    @Override
    protected void initializeExtensions() {
        super.initializeExtensions();
        install(new TestingFunctions(), TestingFunctions.class);
    }

    /**
     * Returns an object to allocate new instances of this class.
     * 
     * @param console
     *            the console object
     * @param test
     *            the test descriptor
     * @param scriptCache
     *            the script cache
     * @return the object allocator to construct new global object instances
     */
    static ObjectAllocator<V8TestGlobalObject> newGlobalObjectAllocator(final ShellConsole console,
            final TestInfo test, final ScriptCache scriptCache) {
        return new ObjectAllocator<V8TestGlobalObject>() {
            @Override
            public V8TestGlobalObject newInstance(Realm realm) {
                return new V8TestGlobalObject(realm, console, test, scriptCache);
            }
        };
    }
}
