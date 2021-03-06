/**
 * Copyright (c) 2012-2015 André Bargull
 * Alle Rechte vorbehalten / All Rights Reserved.  Use is subject to license terms.
 *
 * <https://github.com/anba/es6draft>
 */
package com.github.anba.es6draft.runtime.types.builtins;

import static com.github.anba.es6draft.runtime.AbstractOperations.*;
import static com.github.anba.es6draft.runtime.types.Undefined.UNDEFINED;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.github.anba.es6draft.runtime.ExecutionContext;
import com.github.anba.es6draft.runtime.Realm;
import com.github.anba.es6draft.runtime.internal.CompatibilityOption;
import com.github.anba.es6draft.runtime.internal.IndexedMap;
import com.github.anba.es6draft.runtime.internal.ObjectAllocator;
import com.github.anba.es6draft.runtime.internal.PropertyMap;
import com.github.anba.es6draft.runtime.internal.ScriptException;
import com.github.anba.es6draft.runtime.internal.ScriptIterator;
import com.github.anba.es6draft.runtime.types.Callable;
import com.github.anba.es6draft.runtime.types.Constructor;
import com.github.anba.es6draft.runtime.types.Intrinsics;
import com.github.anba.es6draft.runtime.types.Property;
import com.github.anba.es6draft.runtime.types.PropertyDescriptor;
import com.github.anba.es6draft.runtime.types.ScriptObject;
import com.github.anba.es6draft.runtime.types.Symbol;
import com.github.anba.es6draft.runtime.types.Type;

/**
 * <h1>9 Ordinary and Exotic Objects Behaviours</h1>
 * <ul>
 * <li>9.1 Ordinary Object Internal Methods and Internal Slots
 * </ul>
 */
public class OrdinaryObject implements ScriptObject {
    private static final int STRING_PROPERTIES_DEFAULT_INITIAL_CAPACITY = 16;
    private static final int SYMBOL_PROPERTIES_DEFAULT_INITIAL_CAPACITY = 4;
    private static final Object[] EMPTY_GETTER_ARGS = new Object[0];
    // Maps for String and Symbol valued property keys
    private final PropertyMap<String, Property> properties;
    private final PropertyMap<Symbol, Property> symbolProperties;
    // Map for indexed properties [0, 2^53 - 1]
    private final IndexedMap<Property> indexedProperties;

    /** [[Realm]] */
    @SuppressWarnings("unused")
    private final Realm realm;

    /** [[Prototype]] */
    private ScriptObject prototype = null;

    /** [[Extensible]] */
    private boolean extensible = true;

    /**
     * Constructs a new Ordinary Object instance.
     * 
     * @param realm
     *            the realm object
     */
    public OrdinaryObject(Realm realm) {
        this.realm = realm;
        this.properties = new PropertyMap<>(STRING_PROPERTIES_DEFAULT_INITIAL_CAPACITY);
        this.symbolProperties = new PropertyMap<>(SYMBOL_PROPERTIES_DEFAULT_INITIAL_CAPACITY);
        this.indexedProperties = new IndexedMap<>();
    }

    /**
     * Constructs a new Ordinary Object instance.
     * 
     * @param realm
     *            the realm object
     * @param prototype
     *            the prototype object
     */
    private OrdinaryObject(Realm realm, ScriptObject prototype) {
        this(realm);
        this.prototype = prototype;
    }

    /**
     * Constructs a new Ordinary Object instance.
     * 
     * @param realm
     *            the realm object
     * @param empty
     *            unused placeholder
     */
    /* package */OrdinaryObject(Realm realm, Void empty) {
        this.realm = realm;
        this.properties = null;
        this.symbolProperties = null;
        this.indexedProperties = null;
    }

    @Override
    public String toString() {
        return String.format("%s@%x: indexed=%s, strings=%s, symbols=%s, extensible=%b", getClass()
                .getSimpleName(), System.identityHashCode(this), indexedProperties, properties
                .keySet(), symbolProperties.keySet(), extensible);
    }

    /**
     * Returns {@code true} if arguments {@code x} and {@code y} are both NaN values and not the
     * same object reference.
     * 
     * @param x
     *            the first argument
     * @param y
     *            the second argument
     * @return {@code true} if x and y are both NaN values
     */
    private static final boolean SameValueNaN(Object x, Object y) {
        return x != y && x instanceof Double && y instanceof Double
                && Double.isNaN(((Double) x).doubleValue())
                && Double.isNaN(((Double) y).doubleValue());
    }

    /**
     * Returns the total number of properties.
     * 
     * @param withSymbols
     *            {@code true} to include symbol properties
     * @return the number of properties
     */
    final int countProperties(boolean withSymbols) {
        return properties.size() + indexedProperties.size()
                + (withSymbols ? symbolProperties.size() : 0);
    }

    /**
     * Appends all own string valued properties to the target list.
     * 
     * @param list
     *            the target list
     */
    final void appendProperties(List<? super String> list) {
        if (!properties.isEmpty()) {
            list.addAll(properties.keySet());
        }
    }

    /**
     * Appends all own symbol valued properties to the target list.
     * 
     * @param list
     *            the target list
     */
    final void appendSymbolProperties(List<? super Symbol> list) {
        if (!symbolProperties.isEmpty()) {
            list.addAll(symbolProperties.keySet());
        }
    }

    /**
     * Appends all own integer indexed properties to the target list.
     * 
     * @param list
     *            the target list
     */
    final void appendIndexedProperties(List<? super String> list) {
        if (!indexedProperties.isEmpty()) {
            list.addAll(indexedProperties.keys());
        }
    }

    final void defineOwnPropertyUnchecked(String propertyKey, Property property) {
        // Same as infallibleDefineOwnProperty except extensible check removed.
        assert !IndexedMap.isIndex(IndexedMap.toIndex(propertyKey));
        assert !properties.containsKey(propertyKey) : "illegal property = " + propertyKey;
        properties.put(propertyKey, property);
    }

    public final void infallibleDefineOwnProperty(String propertyKey, Property property) {
        assert extensible : "object not extensible";
        assert !IndexedMap.isIndex(IndexedMap.toIndex(propertyKey));
        assert !properties.containsKey(propertyKey) : "illegal property = " + propertyKey;
        properties.put(propertyKey, property);
    }

    public final void infallibleDefineOwnProperty(Symbol propertyKey, Property property) {
        assert extensible : "object not extensible";
        assert !symbolProperties.containsKey(propertyKey) : "illegal property = " + propertyKey;
        symbolProperties.put(propertyKey, property);
    }

    public final Property lookupOwnProperty(String propertyKey) {
        assert !IndexedMap.isIndex(IndexedMap.toIndex(propertyKey));
        return properties.get(propertyKey);
    }

    public final Property lookupOwnProperty(Symbol propertyKey) {
        return symbolProperties.get(propertyKey);
    }

    /**
     * Returns the indexed properties length.
     * 
     * @return the indexed properties length
     */
    long getIndexedLength() {
        return indexedProperties.getLength();
    }

    /**
     * Returns the own property value from the given index.
     * 
     * @param propertyKey
     *            the indexed property key
     * @return the property value
     */
    Object getIndexed(int propertyKey) {
        return indexedProperties.get(propertyKey).getValue();
    }

    /**
     * Set the own property value at the given index to the new value.
     * 
     * @param propertyKey
     *            the indexed property key
     * @param value
     *            the property value
     */
    final void setIndexed(int propertyKey, Object value) {
        indexedProperties.put(propertyKey, new Property(value, true, true, true));
    }

    /**
     * Deletes all indexed properties within the range {@code [startIndex, endIndex)} in reverse
     * order, i.e. starting from index {@code endIndex - 1}. The range must not be empty.
     * 
     * @param startIndex
     *            the start index (inclusive)
     * @param endIndex
     *            the end index (exclusive)
     * @return {@code -1} if all indexed properties over the requested range have been removed
     *         successfully; otherwise the index of the first property which is not deletable.
     */
    final long deleteRange(long startIndex, long endIndex) {
        assert startIndex < endIndex;
        IndexedMap<Property> indexed = indexedProperties;
        if (indexed.isEmpty()) {
            return -1;
        }
        long lastIndex;
        if (indexed.isSparse()) {
            lastIndex = deleteRangeSparse(startIndex, endIndex);
        } else {
            lastIndex = deleteRangeDense(startIndex, endIndex);
        }
        // Need to call updateLength() manually.
        indexed.updateLength();
        return lastIndex;
    }

    private long deleteRangeDense(long startIndex, long endIndex) {
        IndexedMap<Property> indexed = indexedProperties;
        for (long index = endIndex; startIndex < index;) {
            Property prop = indexed.get(--index);
            if (prop != null && !prop.isConfigurable()) {
                return index;
            }
            indexed.removeUnchecked(index);
        }
        return -1;
    }

    private long deleteRangeSparse(long startIndex, long endIndex) {
        for (Iterator<Map.Entry<Long, Property>> iter = indexedProperties.descendingIterator(
                startIndex, endIndex); iter.hasNext();) {
            Map.Entry<Long, Property> entry = iter.next();
            if (!entry.getValue().isConfigurable()) {
                return entry.getKey();
            }
            // Cannot call remove[Unchecked]() directly b/c of ConcurrentModificationException.
            iter.remove();
        }
        return -1;
    }

    /**
     * Returns the list of integer indexed properties.
     * 
     * @return the list of integer indexed properties
     */
    public final long[] indices() {
        return indexedProperties.indices();
    }

    /**
     * Returns the list of integer indexed properties.
     * 
     * @param from
     *            from index (inclusive)
     * @param to
     *            to index (exclusive)
     * @return the list of integer indexed properties
     */
    public final long[] indices(long from, long to) {
        return indexedProperties.indices(from, to);
    }

    /**
     * Returns {@code true} if this object has indexed properties.
     * 
     * @return {@code true} if this object has indexed properties
     */
    public final boolean hasIndexedProperties() {
        return !indexedProperties.isEmpty();
    }

    /**
     * Returns {@code true} if the object has indexed accessors.
     * 
     * @return {@code true} if the object has indexed accessors
     */
    public boolean hasIndexedAccessors() {
        if (indexedProperties.isEmpty()) {
            return false;
        }
        for (Iterator<Property> iter = indexedProperties.valuesIterator(); iter.hasNext();) {
            if (iter.next().isAccessorDescriptor()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the object's length.
     * 
     * @return the length or {@code -1} if not available
     */
    public long getLength() {
        Property length = ordinaryGetOwnProperty("length");
        if (length == null || !length.isDataDescriptor() || !Type.isNumber(length.getValue())) {
            return -1;
        }
        return ToLength(Type.numberValue(length.getValue()));
    }

    /**
     * Returns {@code true} if the array is dense and has no indexed accessors.
     * 
     * @return {@code true} if the array is dense
     */
    public final boolean isDenseArray() {
        return isDenseArray(getLength());
    }

    /**
     * Returns {@code true} if the array is dense and has no indexed accessors.
     * 
     * @param length
     *            the array length
     * @return {@code true} if the array is dense
     */
    public final boolean isDenseArray(long length) {
        assert !hasSpecialIndexedProperties() : "cannot report dense if special indexed present";
        IndexedMap<Property> ix = indexedProperties;
        return !hasIndexedAccessors() && ix.getLength() == length && !ix.isSparse()
                && !ix.hasHoles();
    }

    /**
     * Returns {@code true} if this object has "special" indexed properties.
     * 
     * @return {@code true} if this object has special indexed properties
     */
    public boolean hasSpecialIndexedProperties() {
        return false;
    }

    /**
     * Returns the array's indexed property values. Only applicable for dense arrays.
     * 
     * @return the array's indexed values
     */
    public final Object[] toArray() {
        return toArray(getLength());
    }

    /**
     * Returns the array's indexed property values. Only applicable for dense arrays.
     * 
     * @param length
     *            the array length
     * @return the array's indexed values
     */
    public final Object[] toArray(long length) {
        assert isDenseArray(length);
        assert 0 <= length && length <= Integer.MAX_VALUE : "length=" + length;
        int len = (int) length;
        Object[] values = new Object[len];
        for (int i = 0; i < len; ++i) {
            values[i] = getIndexed(i);
        }
        return values;
    }

    /**
     * [[Prototype]]
     * 
     * @return the prototype object
     */
    public final ScriptObject getPrototype() {
        return prototype;
    }

    /**
     * [[Prototype]]
     * 
     * @param prototype
     *            the new prototype object
     */
    public final void setPrototype(ScriptObject prototype) {
        this.prototype = prototype;
    }

    /**
     * [[Extensible]]
     * 
     * @return {@code true} if this object is extensible
     */
    protected final boolean isExtensible() {
        return extensible;
    }

    /**
     * [[Extensible]]
     * 
     * @param extensible
     *            the new extensible mode
     */
    protected final void setExtensible(boolean extensible) {
        assert this.extensible || !extensible;
        this.extensible = extensible;
    }

    /**
     * [[HasOwnProperty]] (P)
     *
     * @param cx
     *            the execution context
     * @param propertyKey
     *            the property key
     * @return {@code true} if an own property was found
     */
    protected boolean hasOwnProperty(ExecutionContext cx, long propertyKey) {
        return ordinaryHasOwnProperty(propertyKey);
    }

    /**
     * [[HasOwnProperty]] (P)
     *
     * @param cx
     *            the execution context
     * @param propertyKey
     *            the property key
     * @return {@code true} if an own property was found
     */
    protected boolean hasOwnProperty(ExecutionContext cx, String propertyKey) {
        return ordinaryHasOwnProperty(propertyKey);
    }

    /**
     * [[HasOwnProperty]] (P)
     *
     * @param cx
     *            the execution context
     * @param propertyKey
     *            the property key
     * @return {@code true} if an own property was found
     */
    protected boolean hasOwnProperty(ExecutionContext cx, Symbol propertyKey) {
        return ordinaryHasOwnProperty(propertyKey);
    }

    /**
     * OrdinaryHasOwnProperty (P) (not in spec)
     * 
     * @param propertyKey
     *            the property key
     * @return {@code true} if an own property was found
     */
    protected final boolean ordinaryHasOwnProperty(long propertyKey) {
        // optimized: HasOwnProperty(cx, this, propertyKey)
        return indexedProperties.containsKey(propertyKey);
    }

    /**
     * OrdinaryHasOwnProperty (P) (not in spec)
     * 
     * @param propertyKey
     *            the property key
     * @return {@code true} if an own property was found
     */
    protected final boolean ordinaryHasOwnProperty(String propertyKey) {
        // optimized: HasOwnProperty(cx, this, propertyKey)
        return properties.containsKey(propertyKey);
    }

    /**
     * OrdinaryHasOwnProperty (P) (not in spec)
     * 
     * @param propertyKey
     *            the property key
     * @return {@code true} if an own property was found
     */
    protected final boolean ordinaryHasOwnProperty(Symbol propertyKey) {
        // optimized: HasOwnProperty(cx, this, propertyKey)
        return symbolProperties.containsKey(propertyKey);
    }

    /** 9.1.1 [[GetPrototypeOf]] ( ) */
    @Override
    public ScriptObject getPrototypeOf(ExecutionContext cx) {
        /* step 1 */
        return prototype;
    }

    /** 9.1.2 [[SetPrototypeOf]] (V) */
    @Override
    public boolean setPrototypeOf(ExecutionContext cx, ScriptObject prototype) {
        /* steps 1-3 */
        boolean extensible = this.extensible;
        ScriptObject current = this.prototype;
        /* step 4 */
        if (prototype == current) { // SameValue(prototype, current)
            return true;
        }
        /* step 5 */
        if (!extensible) {
            return false;
        }
        /* steps 6-8 */
        for (ScriptObject p = prototype; p != null;) {
            if (p == this) { // SameValue(p, O)
                return false;
            }
            if (!(p instanceof OrdinaryObject)) {
                break;
            }
            p = ((OrdinaryObject) p).prototype;
        }
        /* step 9 */
        this.prototype = prototype;
        /* step 10 */
        return true;
    }

    /** 9.1.3 [[IsExtensible]] ( ) */
    @Override
    public boolean isExtensible(ExecutionContext cx) {
        /* step 1 */
        return extensible;
    }

    /** 9.1.4 [[PreventExtensions]] ( ) */
    @Override
    public boolean preventExtensions(ExecutionContext cx) {
        /* step 1 */
        this.extensible = false;
        /* step 2 */
        return true;
    }

    /** 9.1.5 [[GetOwnProperty]] (P) */
    @Override
    public final Property getOwnProperty(ExecutionContext cx, long propertyKey) {
        if (IndexedMap.isIndex(propertyKey)) {
            return getProperty(cx, propertyKey);
        }
        return getProperty(cx, ToString(propertyKey));
    }

    /** 9.1.5 [[GetOwnProperty]] (P) */
    @Override
    public final Property getOwnProperty(ExecutionContext cx, String propertyKey) {
        long index = IndexedMap.toIndex(propertyKey);
        if (IndexedMap.isIndex(index)) {
            return getProperty(cx, index);
        }
        return getProperty(cx, propertyKey);
    }

    /** 9.1.5 [[GetOwnProperty]] (P) */
    @Override
    public final Property getOwnProperty(ExecutionContext cx, Symbol propertyKey) {
        /* step 1 */
        return getProperty(cx, propertyKey);
    }

    /**
     * 9.1.5 [[GetOwnProperty]] (P)
     * 
     * @param cx
     *            the execution context
     * @param propertyKey
     *            the property key
     * @return the property or {@code null} if none found
     */
    protected Property getProperty(ExecutionContext cx, long propertyKey) {
        return ordinaryGetOwnProperty(propertyKey);
    }

    /**
     * 9.1.5 [[GetOwnProperty]] (P)
     * 
     * @param cx
     *            the execution context
     * @param propertyKey
     *            the property key
     * @return the property or {@code null} if none found
     */
    protected Property getProperty(ExecutionContext cx, String propertyKey) {
        return ordinaryGetOwnProperty(propertyKey);
    }

    /**
     * 9.1.5 [[GetOwnProperty]] (P)
     * 
     * @param cx
     *            the execution context
     * @param propertyKey
     *            the property key
     * @return the property or {@code null} if none found
     */
    protected Property getProperty(ExecutionContext cx, Symbol propertyKey) {
        return ordinaryGetOwnProperty(propertyKey);
    }

    /**
     * 9.1.5.1 OrdinaryGetOwnProperty (O, P)
     * 
     * @param propertyKey
     *            the property key
     * @return the property record or {@code null} if none found
     */
    protected final Property ordinaryGetOwnProperty(long propertyKey) {
        /* steps 1-9 (altered: returns live view) */
        return indexedProperties.get(propertyKey);
    }

    /**
     * 9.1.5.1 OrdinaryGetOwnProperty (O, P)
     * 
     * @param propertyKey
     *            the property key
     * @return the property record or {@code null} if none found
     */
    protected final Property ordinaryGetOwnProperty(String propertyKey) {
        /* steps 1-9 (altered: returns live view) */
        return properties.get(propertyKey);
    }

    /**
     * 9.1.5.1 OrdinaryGetOwnProperty (O, P)
     * 
     * @param propertyKey
     *            the property key
     * @return the property record or {@code null} if none found
     */
    protected final Property ordinaryGetOwnProperty(Symbol propertyKey) {
        /* steps 1-9 (altered: returns live view) */
        return symbolProperties.get(propertyKey);
    }

    /** 9.1.6 [[DefineOwnProperty]] (P, Desc) */
    @Override
    public final boolean defineOwnProperty(ExecutionContext cx, long propertyKey,
            PropertyDescriptor desc) {
        if (IndexedMap.isIndex(propertyKey)) {
            return defineProperty(cx, propertyKey, desc);
        }
        return defineProperty(cx, ToString(propertyKey), desc);
    }

    /** 9.1.6 [[DefineOwnProperty]] (P, Desc) */
    @Override
    public final boolean defineOwnProperty(ExecutionContext cx, String propertyKey,
            PropertyDescriptor desc) {
        long index = IndexedMap.toIndex(propertyKey);
        if (IndexedMap.isIndex(index)) {
            return defineProperty(cx, index, desc);
        }
        return defineProperty(cx, propertyKey, desc);
    }

    /** 9.1.6 [[DefineOwnProperty]] (P, Desc) */
    @Override
    public final boolean defineOwnProperty(ExecutionContext cx, Symbol propertyKey,
            PropertyDescriptor desc) {
        return defineProperty(cx, propertyKey, desc);
    }

    /**
     * 9.1.6 [[DefineOwnProperty]] (P, Desc)
     * 
     * @param cx
     *            the execution context
     * @param propertyKey
     *            the property key
     * @param desc
     *            the property descriptor
     * @return {@code true} if the property was successfully defined
     */
    protected boolean defineProperty(ExecutionContext cx, long propertyKey, PropertyDescriptor desc) {
        return ordinaryDefineOwnProperty(cx, propertyKey, desc);
    }

    /**
     * 9.1.6 [[DefineOwnProperty]] (P, Desc)
     * 
     * @param cx
     *            the execution context
     * @param propertyKey
     *            the property key
     * @param desc
     *            the property descriptor
     * @return {@code true} if the property was successfully defined
     */
    protected boolean defineProperty(ExecutionContext cx, String propertyKey,
            PropertyDescriptor desc) {
        return ordinaryDefineOwnProperty(cx, propertyKey, desc);
    }

    /**
     * 9.1.6 [[DefineOwnProperty]] (P, Desc)
     * 
     * @param cx
     *            the execution context
     * @param propertyKey
     *            the property key
     * @param desc
     *            the property descriptor
     * @return {@code true} if the property was successfully defined
     */
    protected boolean defineProperty(ExecutionContext cx, Symbol propertyKey,
            PropertyDescriptor desc) {
        return ordinaryDefineOwnProperty(cx, propertyKey, desc);
    }

    /**
     * 9.1.6.1 OrdinaryDefineOwnProperty (O, P, Desc)
     * 
     * @param cx
     *            the execution context
     * @param propertyKey
     *            the property key
     * @param desc
     *            the property descriptor
     * @return {@code true} on success
     */
    protected final boolean ordinaryDefineOwnProperty(ExecutionContext cx, long propertyKey,
            PropertyDescriptor desc) {
        /* steps 1-2 */
        Property current = getProperty(cx, propertyKey);
        /* step 3 */
        boolean extensible = isExtensible();
        /* step 4 */
        return validateAndApplyPropertyDescriptor(indexedProperties, propertyKey, extensible, desc,
                current);
    }

    /**
     * 9.1.6.1 OrdinaryDefineOwnProperty (O, P, Desc)
     * 
     * @param cx
     *            the execution context
     * @param propertyKey
     *            the property key
     * @param desc
     *            the property descriptor
     * @return {@code true} on success
     */
    protected final boolean ordinaryDefineOwnProperty(ExecutionContext cx, String propertyKey,
            PropertyDescriptor desc) {
        /* steps 1-2 */
        Property current = getProperty(cx, propertyKey);
        /* step 3 */
        boolean extensible = isExtensible();
        /* step 4 */
        return validateAndApplyPropertyDescriptor(properties, propertyKey, extensible, desc,
                current);
    }

    /**
     * 9.1.6.1 OrdinaryDefineOwnProperty (O, P, Desc)
     * 
     * @param cx
     *            the execution context
     * @param propertyKey
     *            the property key
     * @param desc
     *            the property descriptor
     * @return {@code true} on success
     */
    protected final boolean ordinaryDefineOwnProperty(ExecutionContext cx, Symbol propertyKey,
            PropertyDescriptor desc) {
        /* steps 1-2 */
        Property current = getProperty(cx, propertyKey);
        /* step 3 */
        boolean extensible = isExtensible();
        /* step 4 */
        return validateAndApplyPropertyDescriptor(symbolProperties, propertyKey, extensible, desc,
                current);
    }

    /**
     * 9.1.6.2 IsCompatiblePropertyDescriptor (Extensible, Desc, Current)
     * 
     * @param extensible
     *            the extensible mode
     * @param desc
     *            the property descriptor
     * @param current
     *            the current property
     * @return {@code true} if <var>desc</var> is compatible
     */
    protected static final boolean IsCompatiblePropertyDescriptor(boolean extensible,
            PropertyDescriptor desc, Property current) {
        /* step 1 */
        return validateAndApplyPropertyDescriptor(null, null, extensible, desc, current);
    }

    /**
     * 9.1.6.3 ValidateAndApplyPropertyDescriptor (O, P, extensible, Desc, current)
     * 
     * @param object
     *            the script object
     * @param propertyKey
     *            the property key
     * @param extensible
     *            the extensible mode
     * @param desc
     *            the property descriptor
     * @param current
     *            the current property
     * @return {@code true} on success
     */
    protected static final boolean ValidateAndApplyPropertyDescriptor(OrdinaryObject object,
            long propertyKey, boolean extensible, PropertyDescriptor desc, Property current) {
        return validateAndApplyPropertyDescriptor(object.indexedProperties, propertyKey,
                extensible, desc, current);
    }

    /**
     * 9.1.6.3 ValidateAndApplyPropertyDescriptor (O, P, extensible, Desc, current)
     * 
     * @param object
     *            the script object
     * @param propertyKey
     *            the property key
     * @param extensible
     *            the extensible mode
     * @param desc
     *            the property descriptor
     * @param current
     *            the current property
     * @return {@code true} on success
     */
    protected static final boolean ValidateAndApplyPropertyDescriptor(OrdinaryObject object,
            String propertyKey, boolean extensible, PropertyDescriptor desc, Property current) {
        return validateAndApplyPropertyDescriptor(object.properties, propertyKey, extensible, desc,
                current);
    }

    /**
     * 9.1.6.3 ValidateAndApplyPropertyDescriptor (O, P, extensible, Desc, current)
     * 
     * @param object
     *            the script object
     * @param propertyKey
     *            the property key
     * @param extensible
     *            the extensible mode
     * @param desc
     *            the property descriptor
     * @param current
     *            the current property
     * @return {@code true} on success
     */
    protected static final boolean ValidateAndApplyPropertyDescriptor(OrdinaryObject object,
            Symbol propertyKey, boolean extensible, PropertyDescriptor desc, Property current) {
        return validateAndApplyPropertyDescriptor(object.symbolProperties, propertyKey, extensible,
                desc, current);
    }

    /**
     * 9.1.6.3 ValidateAndApplyPropertyDescriptor (O, P, extensible, Desc, current)
     * 
     * @param object
     *            the script object
     * @param propertyKey
     *            the property key
     * @param extensible
     *            the extensible mode
     * @param desc
     *            the property descriptor
     * @param current
     *            the current property
     * @return {@code true} on success
     */
    private static final <KEY> boolean validateAndApplyPropertyDescriptor(
            PropertyMap<KEY, Property> object, KEY propertyKey, boolean extensible,
            PropertyDescriptor desc, Property current) {
        /* step 1 */
        assert (object == null || propertyKey != null);
        /* step 2 */
        if (current == null) {
            if (!extensible) {
                return false;
            }
            if (desc.isGenericDescriptor() || desc.isDataDescriptor()) {
                if (object != null) {
                    object.put(propertyKey, desc.toProperty());
                }
            } else {
                assert desc.isAccessorDescriptor();
                if (object != null) {
                    object.put(propertyKey, desc.toProperty());
                }
            }
            return true;
        }
        /* step 3 */
        if (desc.isEmpty()) {
            return true;
        }
        /* step 4 */
        if (current.isSubset(desc)) {
            return true;
        }
        /* step 5 */
        if (!current.isConfigurable()) {
            if (desc.isConfigurable()) {
                return false;
            }
            if (desc.hasEnumerable() && desc.isEnumerable() != current.isEnumerable()) {
                return false;
            }
        }
        if (desc.isGenericDescriptor()) {
            /* step 6 */
            // no further validation required, proceed below...
        } else if (desc.isDataDescriptor() != current.isDataDescriptor()) {
            /* step 7 */
            if (!current.isConfigurable()) {
                return false;
            }
            if (current.isDataDescriptor()) {
                if (object != null) {
                    object.get(propertyKey).toAccessorProperty();
                }
            } else {
                if (object != null) {
                    object.get(propertyKey).toDataProperty();
                }
            }
        } else if (desc.isDataDescriptor() && current.isDataDescriptor()) {
            /* step 8 */
            if (!current.isConfigurable()) {
                if (!current.isWritable() && desc.isWritable()) {
                    return false;
                }
                if (!current.isWritable()) {
                    if (desc.hasValue() && !SameValue(desc.getValue(), current.getValue())) {
                        return false;
                    }
                }
            }
        } else {
            /* step 9 */
            assert desc.isAccessorDescriptor() && current.isAccessorDescriptor();
            if (!current.isConfigurable()) {
                if (desc.hasSetter() && desc.getSetter() != current.getSetter()) {
                    return false;
                }
                if (desc.hasGetter() && desc.getGetter() != current.getGetter()) {
                    return false;
                }
            }
        }
        /* step 10 */
        if (object != null) {
            object.get(propertyKey).apply(desc);
        }
        /* step 11 */
        return true;
    }

    /**
     * 9.1.6.3 ValidateAndApplyPropertyDescriptor (O, P, extensible, Desc, current)
     * 
     * @param object
     *            the script object
     * @param propertyKey
     *            the property key
     * @param extensible
     *            the extensible mode
     * @param desc
     *            the property descriptor
     * @param current
     *            the current property
     * @return {@code true} on success
     */
    private static final boolean validateAndApplyPropertyDescriptor(IndexedMap<Property> object,
            long propertyKey, boolean extensible, PropertyDescriptor desc, Property current) {
        /* step 1 */
        assert (object == null || IndexedMap.isIndex(propertyKey));
        /* step 2 */
        if (current == null) {
            if (!extensible) {
                return false;
            }
            if (desc.isGenericDescriptor() || desc.isDataDescriptor()) {
                if (object != null) {
                    object.put(propertyKey, desc.toProperty());
                }
            } else {
                assert desc.isAccessorDescriptor();
                if (object != null) {
                    object.put(propertyKey, desc.toProperty());
                }
            }
            return true;
        }
        /* step 3 */
        if (desc.isEmpty()) {
            return true;
        }
        /* step 4 */
        if (current.isSubset(desc)) {
            return true;
        }
        /* step 5 */
        if (!current.isConfigurable()) {
            if (desc.isConfigurable()) {
                return false;
            }
            if (desc.hasEnumerable() && desc.isEnumerable() != current.isEnumerable()) {
                return false;
            }
        }
        if (desc.isGenericDescriptor()) {
            /* step 6 */
            // no further validation required, proceed below...
        } else if (desc.isDataDescriptor() != current.isDataDescriptor()) {
            /* step 7 */
            if (!current.isConfigurable()) {
                return false;
            }
            if (current.isDataDescriptor()) {
                if (object != null) {
                    object.get(propertyKey).toAccessorProperty();
                }
            } else {
                if (object != null) {
                    object.get(propertyKey).toDataProperty();
                }
            }
        } else if (desc.isDataDescriptor() && current.isDataDescriptor()) {
            /* step 8 */
            if (!current.isConfigurable()) {
                if (!current.isWritable() && desc.isWritable()) {
                    return false;
                }
                if (!current.isWritable()) {
                    if (desc.hasValue() && !SameValue(desc.getValue(), current.getValue())) {
                        return false;
                    }
                }
            }
        } else {
            /* step 9 */
            assert desc.isAccessorDescriptor() && current.isAccessorDescriptor();
            if (!current.isConfigurable()) {
                if (desc.hasSetter() && desc.getSetter() != current.getSetter()) {
                    return false;
                }
                if (desc.hasGetter() && desc.getGetter() != current.getGetter()) {
                    return false;
                }
            }
        }
        /* step 10 */
        if (object != null) {
            object.get(propertyKey).apply(desc);
        }
        /* step 11 */
        return true;
    }

    /**
     * 9.1.7 [[HasProperty]](P)
     */
    @Override
    public final boolean hasProperty(ExecutionContext cx, long propertyKey) {
        if (IndexedMap.isIndex(propertyKey)) {
            return has(cx, propertyKey);
        }
        return has(cx, ToString(propertyKey));
    }

    /**
     * 9.1.7 [[HasProperty]](P)
     */
    @Override
    public final boolean hasProperty(ExecutionContext cx, String propertyKey) {
        long index = IndexedMap.toIndex(propertyKey);
        if (IndexedMap.isIndex(index)) {
            return has(cx, index);
        }
        return has(cx, propertyKey);
    }

    /**
     * 9.1.7 [[HasProperty]](P)
     */
    @Override
    public final boolean hasProperty(ExecutionContext cx, Symbol propertyKey) {
        return has(cx, propertyKey);
    }

    /**
     * 9.1.7 [[HasProperty]](P)
     * 
     * @param cx
     *            the execution context
     * @param propertyKey
     *            the property key
     * @return {@code true} if the property was found
     */
    protected boolean has(ExecutionContext cx, long propertyKey) {
        return ordinaryHasProperty(cx, propertyKey);
    }

    /**
     * 9.1.7 [[HasProperty]](P)
     * 
     * @param cx
     *            the execution context
     * @param propertyKey
     *            the property key
     * @return {@code true} if the property was found
     */
    protected boolean has(ExecutionContext cx, String propertyKey) {
        return ordinaryHasProperty(cx, propertyKey);
    }

    /**
     * 9.1.7 [[HasProperty]](P)
     * 
     * @param cx
     *            the execution context
     * @param propertyKey
     *            the property key
     * @return {@code true} if the property was found
     */
    protected boolean has(ExecutionContext cx, Symbol propertyKey) {
        return ordinaryHasProperty(cx, propertyKey);
    }

    /**
     * 9.1.7.1 OrdinaryHasProperty (O, P)
     * 
     * @param cx
     *            the execution context
     * @param propertyKey
     *            the property key
     * @return {@code true} if the property was found
     */
    protected final boolean ordinaryHasProperty(ExecutionContext cx, long propertyKey) {
        /* step 1 (implicit) */
        /* step 2 */
        boolean hasOwn = ordinaryHasOwnProperty(propertyKey);
        /* step 3 */
        if (hasOwn) {
            return true;
        }
        /* steps 4-5 */
        ScriptObject parent = getPrototypeOf(cx);
        /* step 6 */
        if (parent != null) {
            return parent.hasProperty(cx, propertyKey);
        }
        /* step 7 */
        return false;
    }

    /**
     * 9.1.7.1 OrdinaryHasProperty (O, P)
     * 
     * @param cx
     *            the execution context
     * @param propertyKey
     *            the property key
     * @return {@code true} if the property was found
     */
    protected final boolean ordinaryHasProperty(ExecutionContext cx, String propertyKey) {
        /* step 1 (implicit) */
        /* step 2 */
        boolean hasOwn = ordinaryHasOwnProperty(propertyKey);
        /* step 3 */
        if (hasOwn) {
            return true;
        }
        /* steps 4-5 */
        ScriptObject parent = getPrototypeOf(cx);
        /* step 6 */
        if (parent != null) {
            return parent.hasProperty(cx, propertyKey);
        }
        /* step 7 */
        return false;
    }

    /**
     * 9.1.7.1 OrdinaryHasProperty (O, P)
     * 
     * @param cx
     *            the execution context
     * @param propertyKey
     *            the property key
     * @return {@code true} if the property was found
     */
    protected final boolean ordinaryHasProperty(ExecutionContext cx, Symbol propertyKey) {
        /* step 1 (implicit) */
        /* step 2 */
        boolean hasOwn = ordinaryHasOwnProperty(propertyKey);
        /* step 3 */
        if (hasOwn) {
            return true;
        }
        /* steps 4-5 */
        ScriptObject parent = getPrototypeOf(cx);
        /* step 6 */
        if (parent != null) {
            return parent.hasProperty(cx, propertyKey);
        }
        /* step 7 */
        return false;
    }

    /** 9.1.8 [[Get]] (P, Receiver) */
    @Override
    public final Object get(ExecutionContext cx, long propertyKey, Object receiver) {
        if (IndexedMap.isIndex(propertyKey)) {
            return getValue(cx, propertyKey, receiver);
        }
        return getValue(cx, ToString(propertyKey), receiver);
    }

    /** 9.1.8 [[Get]] (P, Receiver) */
    @Override
    public final Object get(ExecutionContext cx, String propertyKey, Object receiver) {
        long index = IndexedMap.toIndex(propertyKey);
        if (IndexedMap.isIndex(index)) {
            return getValue(cx, index, receiver);
        }
        return getValue(cx, propertyKey, receiver);
    }

    /** 9.1.8 [[Get]] (P, Receiver) */
    @Override
    public final Object get(ExecutionContext cx, Symbol propertyKey, Object receiver) {
        return getValue(cx, propertyKey, receiver);
    }

    /**
     * 9.1.8 [[Get]] (P, Receiver)
     *
     * @param cx
     *            the execution context
     * @param propertyKey
     *            the property key
     * @param receiver
     *            the receiver object
     * @return the property value
     */
    protected Object getValue(ExecutionContext cx, long propertyKey, Object receiver) {
        /* step 1 (implicit) */
        /* steps 2-3 */
        Property desc = getProperty(cx, propertyKey);
        /* step 4 */
        if (desc == null) {
            ScriptObject parent = getPrototypeOf(cx);
            if (parent == null) {
                return UNDEFINED;
            }
            return parent.get(cx, propertyKey, receiver);
        }
        /* step 5 */
        if (desc.isDataDescriptor()) {
            return desc.getValue();
        }
        assert desc.isAccessorDescriptor();
        /* step 6 */
        Callable getter = desc.getGetter();
        /* step 7 */
        if (getter == null) {
            return UNDEFINED;
        }
        /* step 8 */
        return getter.call(cx, receiver, EMPTY_GETTER_ARGS);
    }

    /**
     * 9.1.8 [[Get]] (P, Receiver)
     *
     * @param cx
     *            the execution context
     * @param propertyKey
     *            the property key
     * @param receiver
     *            the receiver object
     * @return the property value
     */
    protected Object getValue(ExecutionContext cx, String propertyKey, Object receiver) {
        /* step 1 (implicit) */
        /* steps 2-3 */
        Property desc = getProperty(cx, propertyKey);
        /* step 4 */
        if (desc == null) {
            ScriptObject parent = getPrototypeOf(cx);
            if (parent == null) {
                return UNDEFINED;
            }
            return parent.get(cx, propertyKey, receiver);
        }
        /* step 5 */
        if (desc.isDataDescriptor()) {
            return desc.getValue();
        }
        assert desc.isAccessorDescriptor();
        /* step 6 */
        Callable getter = desc.getGetter();
        /* step 7 */
        if (getter == null) {
            return UNDEFINED;
        }
        /* step 8 */
        return getter.call(cx, receiver, EMPTY_GETTER_ARGS);
    }

    /**
     * 9.1.8 [[Get]] (P, Receiver)
     *
     * @param cx
     *            the execution context
     * @param propertyKey
     *            the property key
     * @param receiver
     *            the receiver object
     * @return the property value
     */
    protected Object getValue(ExecutionContext cx, Symbol propertyKey, Object receiver) {
        /* step 1 (implicit) */
        /* steps 2-3 */
        Property desc = getProperty(cx, propertyKey);
        /* step 4 */
        if (desc == null) {
            ScriptObject parent = getPrototypeOf(cx);
            if (parent == null) {
                return UNDEFINED;
            }
            return parent.get(cx, propertyKey, receiver);
        }
        /* step 5 */
        if (desc.isDataDescriptor()) {
            return desc.getValue();
        }
        assert desc.isAccessorDescriptor();
        /* step 6 */
        Callable getter = desc.getGetter();
        /* step 7 */
        if (getter == null) {
            return UNDEFINED;
        }
        /* step 8 */
        return getter.call(cx, receiver, EMPTY_GETTER_ARGS);
    }

    /** 9.1.9 [[Set] (P, V, Receiver) */
    @Override
    public final boolean set(ExecutionContext cx, long propertyKey, Object value, Object receiver) {
        if (IndexedMap.isIndex(propertyKey)) {
            return setValue(cx, propertyKey, value, receiver);
        }
        return setValue(cx, ToString(propertyKey), value, receiver);
    }

    /** 9.1.9 [[Set] (P, V, Receiver) */
    @Override
    public final boolean set(ExecutionContext cx, String propertyKey, Object value, Object receiver) {
        long index = IndexedMap.toIndex(propertyKey);
        if (IndexedMap.isIndex(index)) {
            return setValue(cx, index, value, receiver);
        }
        return setValue(cx, propertyKey, value, receiver);
    }

    /** 9.1.9 [[Set] (P, V, Receiver) */
    @Override
    public final boolean set(ExecutionContext cx, Symbol propertyKey, Object value, Object receiver) {
        return setValue(cx, propertyKey, value, receiver);
    }

    /**
     * 9.1.9 [[Set] (P, V, Receiver)
     * 
     * @param cx
     *            the execution context
     * @param propertyKey
     *            the property key
     * @param value
     *            the new property value
     * @param receiver
     *            the receiver object
     * @return {@code true} on success
     */
    protected boolean setValue(ExecutionContext cx, long propertyKey, Object value, Object receiver) {
        /* step 1 (implicit) */
        /* steps 2-3 */
        Property ownDesc = getProperty(cx, propertyKey);
        /* step 4 */
        if (ownDesc == null) {
            ScriptObject parent = getPrototypeOf(cx);
            if (parent != null) {
                return parent.set(cx, propertyKey, value, receiver);
            } else {
                ownDesc = new Property(UNDEFINED, true, true, true);
            }
        } else if (receiver == this && ownDesc.isWritable()) {
            // Optimize the common case for own, writable properties
            return setPropertyValue(cx, propertyKey, value, ownDesc);
        }
        /* step 5 */
        if (ownDesc.isDataDescriptor()) {
            if (!ownDesc.isWritable()) {
                return false;
            }
            if (!Type.isObject(receiver)) {
                return false;
            }
            ScriptObject _receiver = Type.objectValue(receiver);
            Property existingDescriptor = _receiver.getOwnProperty(cx, propertyKey);
            if (existingDescriptor != null) {
                if (existingDescriptor.isAccessorDescriptor() || !existingDescriptor.isWritable()) {
                    return false;
                }
                PropertyDescriptor valueDesc = new PropertyDescriptor(value);
                return _receiver.defineOwnProperty(cx, propertyKey, valueDesc);
            } else {
                return CreateDataProperty(cx, _receiver, propertyKey, value);
            }
        }
        /* step 6 */
        assert ownDesc.isAccessorDescriptor();
        Callable setter = ownDesc.getSetter();
        if (setter == null) {
            return false;
        }
        setter.call(cx, receiver, value);
        return true;
    }

    protected boolean setPropertyValue(ExecutionContext cx, long propertyKey, Object value,
            Property current) {
        assert current.isDataDescriptor() && current.isWritable();
        if (!SameValueNaN(current.getValue(), value)) {
            current.setValue(value);
        }
        return true;
    }

    /**
     * 9.1.9 [[Set] (P, V, Receiver)
     * 
     * @param cx
     *            the execution context
     * @param propertyKey
     *            the property key
     * @param value
     *            the new property value
     * @param receiver
     *            the receiver object
     * @return {@code true} on success
     */
    protected boolean setValue(ExecutionContext cx, String propertyKey, Object value,
            Object receiver) {
        /* step 1 (implicit) */
        /* steps 2-3 */
        Property ownDesc = getProperty(cx, propertyKey);
        /* step 4 */
        if (ownDesc == null) {
            ScriptObject parent = getPrototypeOf(cx);
            if (parent != null) {
                return parent.set(cx, propertyKey, value, receiver);
            } else {
                ownDesc = new Property(UNDEFINED, true, true, true);
            }
        } else if (receiver == this && ownDesc.isWritable()) {
            // Optimize the common case for own, writable properties
            return setPropertyValue(cx, propertyKey, value, ownDesc);
        }
        /* step 5 */
        if (ownDesc.isDataDescriptor()) {
            if (!ownDesc.isWritable()) {
                return false;
            }
            if (!Type.isObject(receiver)) {
                return false;
            }
            ScriptObject _receiver = Type.objectValue(receiver);
            Property existingDescriptor = _receiver.getOwnProperty(cx, propertyKey);
            if (existingDescriptor != null) {
                if (existingDescriptor.isAccessorDescriptor() || !existingDescriptor.isWritable()) {
                    return false;
                }
                PropertyDescriptor valueDesc = new PropertyDescriptor(value);
                return _receiver.defineOwnProperty(cx, propertyKey, valueDesc);
            } else {
                return CreateDataProperty(cx, _receiver, propertyKey, value);
            }
        }
        /* step 6 */
        assert ownDesc.isAccessorDescriptor();
        Callable setter = ownDesc.getSetter();
        if (setter == null) {
            return false;
        }
        setter.call(cx, receiver, value);
        return true;
    }

    protected boolean setPropertyValue(ExecutionContext cx, String propertyKey, Object value,
            Property current) {
        assert current.isDataDescriptor() && current.isWritable();
        if (!SameValueNaN(current.getValue(), value)) {
            current.setValue(value);
        }
        return true;
    }

    /**
     * 9.1.9 [[Set] (P, V, Receiver)
     * 
     * @param cx
     *            the execution context
     * @param propertyKey
     *            the property key
     * @param value
     *            the new property value
     * @param receiver
     *            the receiver object
     * @return {@code true} on success
     */
    protected boolean setValue(ExecutionContext cx, Symbol propertyKey, Object value,
            Object receiver) {
        /* step 1 (implicit) */
        /* steps 2-3 */
        Property ownDesc = getProperty(cx, propertyKey);
        /* step 4 */
        if (ownDesc == null) {
            ScriptObject parent = getPrototypeOf(cx);
            if (parent != null) {
                return parent.set(cx, propertyKey, value, receiver);
            } else {
                ownDesc = new Property(UNDEFINED, true, true, true);
            }
        } else if (receiver == this && ownDesc.isWritable()) {
            // Optimize the common case for own, writable properties
            return setPropertyValue(cx, propertyKey, value, ownDesc);
        }
        /* step 5 */
        if (ownDesc.isDataDescriptor()) {
            if (!ownDesc.isWritable()) {
                return false;
            }
            if (!Type.isObject(receiver)) {
                return false;
            }
            ScriptObject _receiver = Type.objectValue(receiver);
            Property existingDescriptor = _receiver.getOwnProperty(cx, propertyKey);
            if (existingDescriptor != null) {
                if (existingDescriptor.isAccessorDescriptor() || !existingDescriptor.isWritable()) {
                    return false;
                }
                PropertyDescriptor valueDesc = new PropertyDescriptor(value);
                return _receiver.defineOwnProperty(cx, propertyKey, valueDesc);
            } else {
                return CreateDataProperty(cx, _receiver, propertyKey, value);
            }
        }
        /* step 6 */
        assert ownDesc.isAccessorDescriptor();
        Callable setter = ownDesc.getSetter();
        if (setter == null) {
            return false;
        }
        setter.call(cx, receiver, value);
        return true;
    }

    protected boolean setPropertyValue(ExecutionContext cx, Symbol propertyKey, Object value,
            Property current) {
        assert current.isDataDescriptor() && current.isWritable();
        if (!SameValueNaN(current.getValue(), value)) {
            current.setValue(value);
        }
        return true;
    }

    /** 9.1.10 [[Delete]] (P) */
    @Override
    public final boolean delete(ExecutionContext cx, long propertyKey) {
        if (IndexedMap.isIndex(propertyKey)) {
            return deleteProperty(cx, propertyKey);
        }
        return deleteProperty(cx, ToString(propertyKey));
    }

    /** 9.1.10 [[Delete]] (P) */
    @Override
    public final boolean delete(ExecutionContext cx, String propertyKey) {
        long index = IndexedMap.toIndex(propertyKey);
        if (IndexedMap.isIndex(index)) {
            return deleteProperty(cx, index);
        }
        return deleteProperty(cx, propertyKey);
    }

    /** 9.1.10 [[Delete]] (P) */
    @Override
    public final boolean delete(ExecutionContext cx, Symbol propertyKey) {
        return deleteProperty(cx, propertyKey);
    }

    /**
     * 9.1.10 [[Delete]] (P)
     *
     * @param cx
     *            the execution context
     * @param propertyKey
     *            the property key
     * @return {@code true} if the property was successfully deleted
     */
    protected boolean deleteProperty(ExecutionContext cx, long propertyKey) {
        /* step 1 (not applicable) */
        /* steps 2-3 */
        Property desc = getProperty(cx, propertyKey);
        /* step 4 */
        if (desc == null) {
            return true;
        }
        /* step 5 */
        if (desc.isConfigurable()) {
            indexedProperties.remove(propertyKey);
            return true;
        }
        /* step 6 */
        return false;
    }

    /**
     * 9.1.10 [[Delete]] (P)
     *
     * @param cx
     *            the execution context
     * @param propertyKey
     *            the property key
     * @return {@code true} if the property was successfully deleted
     */
    protected boolean deleteProperty(ExecutionContext cx, String propertyKey) {
        /* step 1 (not applicable) */
        /* steps 2-3 */
        Property desc = getProperty(cx, propertyKey);
        /* step 4 */
        if (desc == null) {
            return true;
        }
        /* step 5 */
        if (desc.isConfigurable()) {
            properties.remove(propertyKey);
            return true;
        }
        /* step 6 */
        return false;
    }

    /**
     * 9.1.10 [[Delete]] (P)
     *
     * @param cx
     *            the execution context
     * @param propertyKey
     *            the property key
     * @return {@code true} if the property was successfully deleted
     */
    protected boolean deleteProperty(ExecutionContext cx, Symbol propertyKey) {
        /* step 1 (not applicable) */
        /* steps 2-3 */
        Property desc = getProperty(cx, propertyKey);
        /* step 4 */
        if (desc == null) {
            return true;
        }
        /* step 5 */
        if (desc.isConfigurable()) {
            symbolProperties.remove(propertyKey);
            return true;
        }
        /* step 6 */
        return false;
    }

    /** 9.1.11 [[Enumerate]] () */
    @Override
    public final ScriptObject enumerate(ExecutionContext cx) {
        return CreateListIterator(cx, new EnumKeysIterator(cx, this));
    }

    /** 9.1.11 [[Enumerate]] () */
    @Override
    public final ScriptIterator<?> enumerateKeys(ExecutionContext cx) {
        return new EnumKeysIterator(cx, this);
    }

    /**
     * 9.1.11 [[Enumerate]] ()
     *
     * @param cx
     *            the execution context
     * @return the list of enumerable string valued property keys
     */
    protected List<String> getEnumerableKeys(ExecutionContext cx) {
        int totalSize = countProperties(false);
        if (totalSize == 0) {
            return Collections.emptyList();
        }
        ArrayList<String> keys = new ArrayList<>(totalSize);
        appendIndexedProperties(keys);
        appendProperties(keys);
        return keys;
    }

    protected enum Enumerability {
        Enumerable, NonEnumerable, Deleted;

        static Enumerability isEnumerable(boolean enumerable) {
            return enumerable ? Enumerable : NonEnumerable;
        }
    }

    /**
     * Subclasses need to override this method if they have virtual, configurable properties.
     * 
     * @param propertyKey
     *            the property key
     * @return the property enumerable status
     */
    protected Enumerability isEnumerableOwnProperty(String propertyKey) {
        Property prop;
        long index = IndexedMap.toIndex(propertyKey);
        if (IndexedMap.isIndex(index)) {
            prop = ordinaryGetOwnProperty(index);
        } else {
            prop = ordinaryGetOwnProperty(propertyKey);
        }
        if (prop == null) {
            return Enumerability.Deleted;
        }
        return Enumerability.isEnumerable(prop.isEnumerable());
    }

    private static final class FakeObject extends OrdinaryObject {
        private final ScriptObject object;

        FakeObject(Realm realm, ScriptObject object) {
            super(realm, (Void) null);
            this.object = object;
        }

        @Override
        public ScriptObject getPrototypeOf(ExecutionContext cx) {
            return object.getPrototypeOf(cx);
        }

        @Override
        protected Enumerability isEnumerableOwnProperty(String propertyKey) {
            return Enumerability.Enumerable;
        }
    }

    private static final class EnumKeysIterator extends
            com.github.anba.es6draft.runtime.internal.SimpleIterator<Object> implements
            com.github.anba.es6draft.runtime.internal.ScriptIterator<Object> {
        private final ExecutionContext cx;
        private OrdinaryObject obj;
        private final HashSet<Object> visitedKeys = new HashSet<>();
        private Iterator<String> keys;
        private ScriptIterator<?> protoKeys;
        private ScriptObject scriptIter;

        EnumKeysIterator(ExecutionContext cx, OrdinaryObject obj) {
            this.cx = cx;
            this.obj = obj;
            this.keys = obj.getEnumerableKeys(cx).iterator();
        }

        @Override
        protected Object findNext() {
            HashSet<Object> visitedKeys = this.visitedKeys;
            for (Iterator<String> keys; (keys = this.keys) != null;) {
                assert protoKeys == null;
                OrdinaryObject obj = this.obj;
                while (keys.hasNext()) {
                    String key = keys.next();
                    Enumerability e = obj.isEnumerableOwnProperty(key);
                    if (e != Enumerability.Deleted) {
                        if (visitedKeys.add(key) && e == Enumerability.Enumerable) {
                            return key;
                        }
                    }
                }
                nextObject();
            }
            if (this.protoKeys != null) {
                return findNextFromProtoKeys();
            }
            return null;
        }

        private Object findNextFromProtoKeys() {
            HashSet<Object> visitedKeys = this.visitedKeys;
            ScriptIterator<?> protoKeys = this.protoKeys;
            try {
                while (protoKeys.hasNext()) {
                    Object key = protoKeys.next();
                    if (visitedKeys.add(key)) {
                        return key;
                    }
                }
                // visited all inherited keys
                this.protoKeys = null;
                return null;
            } catch (ScriptException e) {
                this.protoKeys = null;
                throw e;
            }
        }

        private void nextObject() {
            // switch to prototype enumerate
            try {
                ScriptObject proto = obj.getPrototypeOf(cx);
                if (proto != null) {
                    if (proto instanceof OrdinaryObject) {
                        this.obj = (OrdinaryObject) proto;
                        this.keys = ((OrdinaryObject) proto).getEnumerableKeys(cx).iterator();
                    } else if (cx.getRealm().isEnabled(CompatibilityOption.ProxyProtoSkipEnumerate)) {
                        this.obj = new FakeObject(cx.getRealm(), proto);
                        this.keys = EnumerableOwnNames(cx, proto).iterator();
                    } else {
                        ScriptIterator<?> protoKeys = proto.enumerateKeys(cx);
                        if (protoKeys instanceof EnumKeysIterator) {
                            EnumKeysIterator keysIterator = (EnumKeysIterator) protoKeys;
                            assert keysIterator.visitedKeys.isEmpty();
                            assert keysIterator.keys != null && keysIterator.protoKeys == null;
                            this.obj = keysIterator.obj;
                            this.keys = keysIterator.keys;
                        } else {
                            this.obj = null;
                            this.keys = null;
                            this.protoKeys = protoKeys;
                        }
                    }
                } else {
                    this.obj = null;
                    this.keys = null;
                    this.protoKeys = null;
                }
            } catch (ScriptException e) {
                this.obj = null;
                this.keys = null;
                this.protoKeys = null;
                throw e;
            }
        }

        private boolean isDone() {
            return obj == null && keys == null && protoKeys == null;
        }

        private boolean hasReturn() {
            if (scriptIter != null) {
                return true;
            }
            OrdinaryObject iterProto = cx.getIntrinsic(Intrinsics.IteratorPrototype);
            for (;;) {
                if (iterProto.ordinaryHasOwnProperty("return")) {
                    return true;
                }
                ScriptObject proto = iterProto.getPrototype();
                if (proto == null) {
                    return false;
                }
                if (!(proto instanceof OrdinaryObject)) {
                    return true;
                }
                iterProto = (OrdinaryObject) proto;
            }
        }

        @Override
        public ScriptObject getScriptObject() {
            if (scriptIter == null) {
                scriptIter = CreateListIterator(cx, this);
            }
            return scriptIter;
        }

        @Override
        public void close() throws ScriptException {
            if (!isDone() && hasReturn()) {
                IteratorClose(cx, getScriptObject());
            }
        }

        @Override
        public void close(Throwable cause) throws ScriptException {
            if (!isDone() && hasReturn()) {
                IteratorClose(cx, getScriptObject(), cause);
            }
        }
    }

    /** 9.1.12 [[OwnPropertyKeys]] ( ) */
    @Override
    public final List<?> ownPropertyKeys(ExecutionContext cx) {
        return getOwnPropertyKeys(cx);
    }

    /** 9.1.12 [[OwnPropertyKeys]] ( ) */
    @Override
    public final Iterator<?> ownKeys(ExecutionContext cx) {
        return getOwnPropertyKeys(cx).iterator();
    }

    /**
     * 9.1.12 [[OwnPropertyKeys]] ( )
     * 
     * @param cx
     *            the execution context
     * @return the list of own property keys
     */
    protected List<Object> getOwnPropertyKeys(ExecutionContext cx) {
        int totalSize = countProperties(true);
        if (totalSize == 0) {
            return Collections.emptyList();
        }
        /* step 1 */
        ArrayList<Object> ownKeys = new ArrayList<>(totalSize);
        /* step 2 */
        appendIndexedProperties(ownKeys);
        /* step 3 */
        appendProperties(ownKeys);
        /* step 4 */
        appendSymbolProperties(ownKeys);
        /* step 5 */
        return ownKeys;
    }

    /**
     * 9.1.13 ObjectCreate(proto, internalSlotsList)
     *
     * @param cx
     *            the execution context
     * @param proto
     *            the prototype object
     * @return the new object
     */
    public static final OrdinaryObject ObjectCreate(ExecutionContext cx, ScriptObject proto) {
        return new OrdinaryObject(cx.getRealm(), proto);
    }

    /**
     * 9.1.13 ObjectCreate(proto, internalSlotsList)
     *
     * @param cx
     *            the execution context
     * @param proto
     *            the prototype object
     * @return the new object
     */
    public static final OrdinaryObject ObjectCreate(ExecutionContext cx, Intrinsics proto) {
        return new OrdinaryObject(cx.getRealm(), cx.getIntrinsic(proto));
    }

    /**
     * 9.1.13 ObjectCreate(proto, internalSlotsList)
     *
     * @param <OBJECT>
     *            the object type
     * @param cx
     *            the execution context
     * @param proto
     *            the prototype object
     * @param allocator
     *            the object allocator
     * @return the new object
     */
    public static final <OBJECT extends OrdinaryObject> OBJECT ObjectCreate(ExecutionContext cx,
            ScriptObject proto, ObjectAllocator<OBJECT> allocator) {
        OBJECT obj = allocator.newInstance(cx.getRealm());
        obj.setPrototype(proto);
        return obj;
    }

    /**
     * 9.1.13 ObjectCreate(proto, internalSlotsList)
     *
     * @param <OBJECT>
     *            the object type
     * @param cx
     *            the execution context
     * @param proto
     *            the prototype object
     * @param allocator
     *            the object allocator
     * @return the new object
     */
    public static final <OBJECT extends OrdinaryObject> OBJECT ObjectCreate(ExecutionContext cx,
            Intrinsics proto, ObjectAllocator<OBJECT> allocator) {
        OBJECT obj = allocator.newInstance(cx.getRealm());
        obj.setPrototype(cx.getIntrinsic(proto));
        return obj;
    }

    /**
     * 9.1.13 ObjectCreate(proto, internalSlotsList)
     *
     * @param realm
     *            the realm instance
     * @param proto
     *            the prototype object
     * @return the new object
     */
    public static final OrdinaryObject ObjectCreate(Realm realm, ScriptObject proto) {
        return new OrdinaryObject(realm, proto);
    }

    /**
     * 9.1.13 ObjectCreate(proto, internalSlotsList)
     *
     * @param realm
     *            the realm instance
     * @param proto
     *            the prototype object
     * @return the new object
     */
    public static final OrdinaryObject ObjectCreate(Realm realm, Intrinsics proto) {
        return new OrdinaryObject(realm, realm.getIntrinsic(proto));
    }

    /**
     * 9.1.13 ObjectCreate(proto, internalSlotsList)
     *
     * @param <OBJECT>
     *            the object type
     * @param realm
     *            the realm instance
     * @param proto
     *            the prototype object
     * @param allocator
     *            the object allocator
     * @return the new object
     */
    public static final <OBJECT extends OrdinaryObject> OBJECT ObjectCreate(Realm realm,
            ScriptObject proto, ObjectAllocator<OBJECT> allocator) {
        OBJECT obj = allocator.newInstance(realm);
        obj.setPrototype(proto);
        return obj;
    }

    /**
     * 9.1.13 ObjectCreate(proto, internalSlotsList)
     *
     * @param <OBJECT>
     *            the object type
     * @param realm
     *            the realm instance
     * @param proto
     *            the prototype object
     * @param allocator
     *            the object allocator
     * @return the new object
     */
    public static final <OBJECT extends OrdinaryObject> OBJECT ObjectCreate(Realm realm,
            Intrinsics proto, ObjectAllocator<OBJECT> allocator) {
        OBJECT obj = allocator.newInstance(realm);
        obj.setPrototype(realm.getIntrinsic(proto));
        return obj;
    }

    /**
     * 9.1.14 OrdinaryCreateFromConstructor (constructor, intrinsicDefaultProto, internalSlotsList)
     * 
     * @param cx
     *            the execution context
     * @param constructor
     *            the constructor function
     * @param intrinsicDefaultProto
     *            the default prototype
     * @return the new object
     */
    public static final OrdinaryObject OrdinaryCreateFromConstructor(ExecutionContext cx,
            Constructor constructor, Intrinsics intrinsicDefaultProto) {
        /* step 1 (not applicable) */
        /* steps 2-3 */
        ScriptObject proto = GetPrototypeFromConstructor(cx, constructor, intrinsicDefaultProto);
        /* step 4 */
        return ObjectCreate(cx, proto);
    }

    /**
     * 9.1.14 OrdinaryCreateFromConstructor (constructor, intrinsicDefaultProto, internalSlotsList)
     * 
     * @param <OBJECT>
     *            the object type
     * @param cx
     *            the execution context
     * @param constructor
     *            the constructor function
     * @param intrinsicDefaultProto
     *            the default prototype
     * @param allocator
     *            the object allocator
     * @return the new object
     */
    public static final <OBJECT extends OrdinaryObject> OBJECT OrdinaryCreateFromConstructor(
            ExecutionContext cx, Constructor constructor, Intrinsics intrinsicDefaultProto,
            ObjectAllocator<OBJECT> allocator) {
        /* step 1 (not applicable) */
        /* steps 2-3 */
        ScriptObject proto = GetPrototypeFromConstructor(cx, constructor, intrinsicDefaultProto);
        /* step 4 */
        return ObjectCreate(cx, proto, allocator);
    }

    /**
     * 9.1.15 GetPrototypeFromConstructor ( constructor, intrinsicDefaultProto )
     * 
     * @param cx
     *            the execution context
     * @param constructor
     *            the constructor object
     * @param intrinsicDefaultProto
     *            the default prototype
     * @return the prototype object
     */
    public static ScriptObject GetPrototypeFromConstructor(ExecutionContext cx,
            Constructor constructor, Intrinsics intrinsicDefaultProto) {
        /* steps 1-2 (not applicable) */
        /* steps 3-4 */
        Object proto = Get(cx, constructor, "prototype");
        /* step 5 */
        if (!Type.isObject(proto)) {
            /* steps 5.a-b */
            Realm realm = GetFunctionRealm(cx, constructor);
            /* step 5.c */
            proto = realm.getIntrinsic(intrinsicDefaultProto);
        }
        /* step 6 */
        return Type.objectValue(proto);
    }
}
