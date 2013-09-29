/**
 * Copyright (c) 2012-2013 André Bargull
 * Alle Rechte vorbehalten / All Rights Reserved.  Use is subject to license terms.
 *
 * <https://github.com/anba/es6draft>
 */
package com.github.anba.es6draft.runtime.objects;

import java.util.BitSet;
import java.util.regex.Pattern;

import com.github.anba.es6draft.runtime.Realm;
import com.github.anba.es6draft.runtime.types.builtins.OrdinaryObject;

/**
 * <h1>21 Text Processing</h1><br>
 * <h2>21.2 RegExp (Regular Expression) Objects</h2>
 * <ul>
 * <li>21.2.6 Properties of RegExp Instances
 * </ul>
 */
public class RegExpObject extends OrdinaryObject {
    /** [[OriginalSource]] */
    private String originalSource = null;

    /** [[OriginalFlags]] */
    private String originalFlags = null;

    /** [[RegExpMatcher]] */
    private Pattern regExpMatcher = null;

    private BitSet negativeLAGroups;

    public RegExpObject(Realm realm) {
        super(realm);
    }

    protected void initialise(String originalSource, String originalFlags, Pattern match,
            BitSet negativeLAGroups) {
        this.originalSource = originalSource;
        this.originalFlags = originalFlags;
        this.regExpMatcher = match;
        this.negativeLAGroups = negativeLAGroups;
    }

    protected boolean isInitialised() {
        return regExpMatcher != null;
    }

    protected BitSet getNegativeLookaheadGroups() {
        assert negativeLAGroups != null;
        return negativeLAGroups;
    }

    /**
     * [[OriginalSource]]
     */
    public String getOriginalSource() {
        assert originalSource != null;
        return originalSource;
    }

    /**
     * [[OriginalFlags]]
     */
    public String getOriginalFlags() {
        assert originalFlags != null;
        return originalFlags;
    }

    /**
     * [[RegExpMatcher]]
     */
    public Pattern getRegExpMatcher() {
        assert regExpMatcher != null;
        return regExpMatcher;
    }
}
