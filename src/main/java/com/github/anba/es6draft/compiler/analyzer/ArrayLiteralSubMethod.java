/**
 * Copyright (c) 2012-2015 André Bargull
 * Alle Rechte vorbehalten / All Rights Reserved.  Use is subject to license terms.
 *
 * <https://github.com/anba/es6draft>
 */
package com.github.anba.es6draft.compiler.analyzer;

import java.util.List;

import com.github.anba.es6draft.ast.ArrayLiteral;
import com.github.anba.es6draft.ast.Expression;
import com.github.anba.es6draft.ast.synthetic.ExpressionMethod;
import com.github.anba.es6draft.ast.synthetic.SpreadArrayLiteral;
import com.github.anba.es6draft.ast.synthetic.SpreadElementMethod;

/**
 * Inserts {@link SpreadElementMethod}s into {@link ArrayLiteral} nodes
 */
final class ArrayLiteralSubMethod extends ListSubMethod<ArrayLiteral> {
    private static final int MAX_ARRAY_ELEMENT_SIZE = MAX_EXPR_SIZE;
    private static final int MAX_ARRAY_SIZE = 8 * MAX_ARRAY_ELEMENT_SIZE;
    private static final int MAX_SPREAD_SIZE = 4 * MAX_ARRAY_ELEMENT_SIZE;
    private static final int SPREAD_METHOD_SIZE = 10;

    private static final class ArrayElement extends NodeElement<Expression> {
        ArrayElement(Expression node, int size, int index) {
            super(node, size, index);
        }

        @Override
        protected Expression createReplacement() {
            return new ExpressionMethod(getNode());
        }

        @Override
        protected int getReplacementSize() {
            return EXPR_METHOD_SIZE;
        }
    }

    private static final class ArrayElementMapper implements
            NodeElementMapper<Expression, ArrayElement> {
        @Override
        public ArrayElement map(Expression node, int size, int index) {
            return new ArrayElement(node, size, index);
        }
    }

    private static final class ArrayConflater extends Conflater<ArrayElement, Expression> {
        @Override
        protected int getSourceSize(ArrayElement source) {
            return source.getSize();
        }

        @Override
        protected int getTargetSize() {
            return SPREAD_METHOD_SIZE;
        }

        @Override
        protected Expression newTarget(List<Expression> list) {
            return new SpreadElementMethod(new SpreadArrayLiteral(list));
        }
    }

    @Override
    int processNode(ArrayLiteral node, int oldSize) {
        List<Expression> newElements = newNodes(oldSize, node.getElements(),
                new ArrayElementMapper(), new ArrayConflater(), MAX_ARRAY_ELEMENT_SIZE,
                MAX_ARRAY_SIZE, MAX_SPREAD_SIZE);
        node.setElements(newElements);
        return validateSize(node);
    }
}
