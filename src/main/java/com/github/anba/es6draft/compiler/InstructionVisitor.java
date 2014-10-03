/**
 * Copyright (c) 2012-2014 André Bargull
 * Alle Rechte vorbehalten / All Rights Reserved.  Use is subject to license terms.
 *
 * <https://github.com/anba/es6draft>
 */
package com.github.anba.es6draft.compiler;

import com.github.anba.es6draft.compiler.DefaultCodeGenerator.ValType;
import com.github.anba.es6draft.compiler.assembler.Code.MethodCode;
import com.github.anba.es6draft.compiler.assembler.InstructionAssembler;

/**
 *
 */
class InstructionVisitor extends InstructionAssembler {
    protected InstructionVisitor(MethodCode method) {
        super(method);
    }

    /**
     * value → value, value
     * 
     * @param type
     *            the topmost stack value
     */
    public void dup(ValType type) {
        switch (type.size()) {
        case 1:
            dup();
            return;
        case 2:
            dup2();
            return;
        case 0:
        default:
            throw new AssertionError();
        }
    }

    /**
     * value → []
     * 
     * @param type
     *            the topmost stack value
     */
    public void pop(ValType type) {
        switch (type.size()) {
        case 0:
            return;
        case 1:
            pop();
            return;
        case 2:
            pop2();
            return;
        default:
            throw new AssertionError();
        }
    }

    /**
     * lvalue, rvalue → rvalue, lvalue, rvalue
     * 
     * @param ltype
     *            the second topmost stack value
     * @param rtype
     *            the topmost stack value
     */
    public void dupX(ValType ltype, ValType rtype) {
        int lsize = ltype.size(), rsize = rtype.size();
        if (lsize == 1 && rsize == 1) {
            dupX1();
        } else if (lsize == 1 && rsize == 2) {
            dup2X1();
        } else if (lsize == 2 && rsize == 1) {
            dupX2();
        } else if (lsize == 2 && rsize == 2) {
            dup2X2();
        } else {
            throw new AssertionError();
        }
    }

    /**
     * lvalue, rvalue → rvalue, lvalue
     * 
     * @param ltype
     *            the second topmost stack value
     * @param rtype
     *            the topmost stack value
     */
    public void swap(ValType ltype, ValType rtype) {
        int lsize = ltype.size(), rsize = rtype.size();
        if (lsize == 1 && rsize == 1) {
            swap();
        } else if (lsize == 1 && rsize == 2) {
            swap1_2();
        } else if (lsize == 2 && rsize == 1) {
            swap2_1();
        } else if (lsize == 2 && rsize == 2) {
            swap2();
        } else {
            throw new AssertionError();
        }
    }

    /**
     * value → boxed
     * 
     * @param type
     *            the value type
     */
    public void toBoxed(ValType type) {
        toBoxed(type.toType());
    }
}
