/**
 * Copyright (c) 2012-2014 André Bargull
 * Alle Rechte vorbehalten / All Rights Reserved.  Use is subject to license terms.
 *
 * <https://github.com/anba/es6draft>
 */
package com.github.anba.es6draft.ast;

import com.github.anba.es6draft.ast.scope.Name;

/**
 * <h1>15 ECMAScript Language: Scripts and Modules</h1><br>
 * <h2>15.3 Modules</h2>
 */
public final class ExportSpecifier extends AstNode {
    private final String importName;
    private final Name localName;
    private final String exportName;

    public ExportSpecifier(long beginPosition, long endPosition, String importName, Name localName,
            String exportName) {
        super(beginPosition, endPosition);
        this.importName = importName;
        this.localName = localName;
        this.exportName = exportName;
    }

    public String getImportName() {
        return importName;
    }

    public Name getLocalName() {
        return localName;
    }

    public String getExportName() {
        return exportName;
    }

    @Override
    public <R, V> R accept(NodeVisitor<R, V> visitor, V value) {
        return visitor.visit(this, value);
    }

    @Override
    public <V> int accept(IntNodeVisitor<V> visitor, V value) {
        return visitor.visit(this, value);
    }

    @Override
    public <V> void accept(VoidNodeVisitor<V> visitor, V value) {
        visitor.visit(this, value);
    }
}
