/**
 * Copyright (c) 2012-2015 André Bargull
 * Alle Rechte vorbehalten / All Rights Reserved.  Use is subject to license terms.
 *
 * <https://github.com/anba/es6draft>
 */
package com.github.anba.es6draft.ast;

import java.util.List;

import com.github.anba.es6draft.ast.scope.TopLevelScope;

/**
 * Super-interface for {@link Script}, {@link Module} and {@link FunctionNode}.
 */
public interface TopLevelNode<STATEMENT extends ModuleItem> extends ScopedNode {
    @Override
    TopLevelScope getScope();

    /**
     * Returns this node's statement list, or <code>null</code> if this node does not contain any
     * statements.
     * 
     * @return the statements
     */
    List<STATEMENT> getStatements();

    /**
     * Updates this node's statement list, may throw an {@link UnsupportedOperationException} if the
     * operation is not supported by this node.
     * 
     * @param statements
     *            the new statements
     */
    void setStatements(List<STATEMENT> statements);

    /**
     * Returns <code>true</code> for nodes with synthetic sub-nodes, <code>false</code> otherwise.
     * 
     * @return <code>true</code> if synthetic sub-nodes are present
     */
    boolean hasSyntheticNodes();

    /**
     * Updates the synthetic node information for this node. Used by code size analysis.
     * 
     * @param syntheticNodes
     *            the synthetic nodes flag
     */
    void setSyntheticNodes(boolean syntheticNodes);
}
