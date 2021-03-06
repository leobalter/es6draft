/**
 * Copyright (c) 2012-2015 André Bargull
 * Alle Rechte vorbehalten / All Rights Reserved.  Use is subject to license terms.
 *
 * <https://github.com/anba/es6draft>
 */
package com.github.anba.es6draft.ast;

import java.util.EnumSet;
import java.util.Set;

/**
 * <h1>13 ECMAScript Language: Statements and Declarations</h1><br>
 * <h2>13.7 Iteration Statements</h2>
 * <ul>
 * <li>13.7.1 The do-while Statement
 * </ul>
 */
public final class DoWhileStatement extends IterationStatement {
    private final Expression test;
    private Statement statement;

    public DoWhileStatement(long beginPosition, long endPosition, EnumSet<Abrupt> abrupt,
            Set<String> labelSet, Expression test, Statement statement) {
        super(beginPosition, endPosition, abrupt, labelSet);
        this.test = test;
        this.statement = statement;
    }

    /**
     * Returns the <tt>do while</tt>-statement's test expression node.
     * 
     * @return the expression node
     */
    public Expression getTest() {
        return test;
    }

    /**
     * Returns the <tt>do while</tt>-statement's statement node.
     * 
     * @return the statement node
     */
    public Statement getStatement() {
        return statement;
    }

    /**
     * Sets the <tt>do while</tt>-statement's statement node.
     * 
     * @param statement
     *            the new statement node
     */
    public void setStatement(Statement statement) {
        this.statement = statement;
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
