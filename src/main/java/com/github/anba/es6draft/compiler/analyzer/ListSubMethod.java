/**
 * Copyright (c) 2012-2015 André Bargull
 * Alle Rechte vorbehalten / All Rights Reserved.  Use is subject to license terms.
 *
 * <https://github.com/anba/es6draft>
 */
package com.github.anba.es6draft.compiler.analyzer;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

import com.github.anba.es6draft.ast.Node;

/**
 * Abstract class for list-based node re-writing
 */
abstract class ListSubMethod<NODE extends Node> extends SubMethod<NODE> {
    protected interface NodeElementMapper<NODE extends Node, ELEMENT> {
        ELEMENT map(NODE node, int size, int index);
    }

    private static final <NODE extends Node, ELEMENT> ArrayList<ELEMENT> from(List<NODE> nodes,
            NodeElementMapper<NODE, ELEMENT> mapper) {
        CodeSizeVisitor visitor = new CodeSizeVisitor();
        CodeSizeHandler handler = new EmptyHandler();
        ArrayList<ELEMENT> list = new ArrayList<>(nodes.size());
        for (int i = 0, len = nodes.size(); i < len; i++) {
            NODE property = nodes.get(i);
            int size = property.accept(visitor, handler);
            list.add(mapper.map(property, size, i));
        }
        return list;
    }

    protected static final <NODE extends Node, ELEMENT extends NodeElement<NODE>> List<NODE> newNodes(
            int oldSize, List<? extends NODE> oldNodes, NodeElementMapper<NODE, ELEMENT> mapper,
            Conflater<ELEMENT, NODE> conflater, int maxElementSize, int maxAccSize,
            int maxConflateSize) {
        ArrayList<NODE> newNodes = new ArrayList<NODE>(oldNodes);
        ArrayList<ELEMENT> elements = from(newNodes, mapper);
        int accSize = oldSize;

        // Replace large elements with methods.
        PriorityQueue<ELEMENT> pq = new PriorityQueue<>(elements);
        while (!pq.isEmpty() && pq.peek().getSize() > maxElementSize) {
            ELEMENT element = pq.remove();

            // export and update entry
            accSize += element.export();
            newNodes.set(element.getIndex(), element.getNode());
        }

        if (accSize > maxAccSize) {
            newNodes = compact(newNodes, elements, mapper, conflater, maxConflateSize);
        }
        return newNodes;
    }

    protected static final <NODE extends Node, ELEMENT extends NodeElement<NODE>> List<NODE> newNodes(
            int oldSize, List<? extends NODE> oldNodes, NodeElementMapper<NODE, ELEMENT> mapper,
            Conflater<ELEMENT, NODE> conflater, int maxAccSize, int maxConflateSize) {
        ArrayList<NODE> newNodes = new ArrayList<NODE>(oldNodes);
        ArrayList<ELEMENT> elements = from(newNodes, mapper);
        int accSize = oldSize;

        if (accSize > maxAccSize) {
            newNodes = compact(newNodes, elements, mapper, conflater, maxConflateSize);
        }
        return newNodes;
    }

    private static final <NODE extends Node, ELEMENT extends NodeElement<NODE>> ArrayList<NODE> compact(
            ArrayList<NODE> newNodes, ArrayList<ELEMENT> elements,
            NodeElementMapper<NODE, ELEMENT> mapper, Conflater<ELEMENT, NODE> conflater,
            int maxConflateSize) {
        // compact multiple elements with inner expressions
        boolean needsRerun = conflater.conflate(elements, newNodes, maxConflateSize);
        while (needsRerun) {
            newNodes = new ArrayList<>(newNodes);
            elements = from(newNodes, mapper);
            needsRerun = conflater.conflate(elements, newNodes, maxConflateSize);
        }
        return newNodes;
    }
}
