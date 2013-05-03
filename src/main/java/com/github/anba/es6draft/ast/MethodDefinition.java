/**
 * Copyright (c) 2012-2013 André Bargull
 * Alle Rechte vorbehalten / All Rights Reserved.  Use is subject to license terms.
 *
 * <https://github.com/anba/es6draft>
 */
package com.github.anba.es6draft.ast;

import java.util.List;

/**
 * <h1>13 Functions and Generators</h1>
 * <ul>
 * <li>13.3 Method Definitions
 * </ul>
 */
public class MethodDefinition extends PropertyDefinition implements FunctionNode {
    private FunctionScope scope;
    private MethodType type;
    private PropertyName propertyName;
    private FormalParameterList parameters;
    private List<StatementListItem> statements;
    private StrictMode strictMode;
    private boolean superReference;
    private String headerSource, bodySource;

    public enum MethodType {
        Function, Generator, Getter, Setter
    }

    public MethodDefinition(FunctionScope scope, MethodType type, PropertyName propertyName,
            FormalParameterList parameters, List<StatementListItem> statements,
            boolean superReference, String headerSource, String bodySource) {
        this.scope = scope;
        this.type = type;
        this.propertyName = propertyName;
        this.parameters = parameters;
        this.statements = statements;
        this.superReference = superReference;
        this.headerSource = headerSource;
        this.bodySource = bodySource;
    }

    @Override
    public FunctionScope getScope() {
        return scope;
    }

    public MethodType getType() {
        return type;
    }

    @Override
    public PropertyName getPropertyName() {
        return propertyName;
    }

    @Override
    public String getFunctionName() {
        return propertyName.getName();
    }

    @Override
    public FormalParameterList getParameters() {
        return parameters;
    }

    @Override
    public List<StatementListItem> getStatements() {
        return statements;
    }

    @Override
    public StrictMode getStrictMode() {
        return strictMode;
    }

    @Override
    public void setStrictMode(StrictMode strictMode) {
        this.strictMode = strictMode;
    }

    @Override
    public String getHeaderSource() {
        return headerSource;
    }

    @Override
    public String getBodySource() {
        return bodySource;
    }

    public boolean hasSuperReference() {
        return superReference;
    }

    @Override
    public <R, V> R accept(NodeVisitor<R, V> visitor, V value) {
        return visitor.visit(this, value);
    }
}
