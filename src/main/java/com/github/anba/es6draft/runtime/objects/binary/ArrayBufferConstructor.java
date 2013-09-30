/**
 * Copyright (c) 2012-2013 André Bargull
 * Alle Rechte vorbehalten / All Rights Reserved.  Use is subject to license terms.
 *
 * <https://github.com/anba/es6draft>
 */
package com.github.anba.es6draft.runtime.objects.binary;

import static com.github.anba.es6draft.runtime.AbstractOperations.OrdinaryCreateFromConstructor;
import static com.github.anba.es6draft.runtime.AbstractOperations.ToInteger;
import static com.github.anba.es6draft.runtime.AbstractOperations.ToNumber;
import static com.github.anba.es6draft.runtime.internal.Errors.throwInternalError;
import static com.github.anba.es6draft.runtime.internal.Errors.throwRangeError;
import static com.github.anba.es6draft.runtime.internal.Errors.throwTypeError;
import static com.github.anba.es6draft.runtime.internal.Properties.createProperties;
import static com.github.anba.es6draft.runtime.types.Undefined.UNDEFINED;
import static com.github.anba.es6draft.runtime.types.builtins.OrdinaryFunction.AddRestrictedFunctionProperties;
import static com.github.anba.es6draft.runtime.types.builtins.OrdinaryFunction.OrdinaryConstruct;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.github.anba.es6draft.runtime.ExecutionContext;
import com.github.anba.es6draft.runtime.Realm;
import com.github.anba.es6draft.runtime.internal.Initialisable;
import com.github.anba.es6draft.runtime.internal.Messages;
import com.github.anba.es6draft.runtime.internal.ObjectAllocator;
import com.github.anba.es6draft.runtime.internal.Properties.Attributes;
import com.github.anba.es6draft.runtime.internal.Properties.Function;
import com.github.anba.es6draft.runtime.internal.Properties.Prototype;
import com.github.anba.es6draft.runtime.internal.Properties.Value;
import com.github.anba.es6draft.runtime.types.BuiltinSymbol;
import com.github.anba.es6draft.runtime.types.Intrinsics;
import com.github.anba.es6draft.runtime.types.ScriptObject;
import com.github.anba.es6draft.runtime.types.Type;
import com.github.anba.es6draft.runtime.types.builtins.BuiltinConstructor;

/**
 * <h1>24 Structured Data</h1><br>
 * <h2>24.1 ArrayBuffer Objects</h2>
 * <ul>
 * <li>24.1.1 Abstract Operations For ArrayBuffer Objects
 * <li>24.1.2 The ArrayBuffer Constructor
 * <li>24.1.3 Properties of the ArrayBuffer Constructor
 * </ul>
 */
public class ArrayBufferConstructor extends BuiltinConstructor implements Initialisable {
    private static final boolean IS_LITTLE_ENDIAN = true;

    public ArrayBufferConstructor(Realm realm) {
        super(realm);
    }

    @Override
    public void initialise(ExecutionContext cx) {
        createProperties(this, cx, Properties.class);
        AddRestrictedFunctionProperties(cx, this);
    }

    private static class ArrayBufferObjectAllocator implements ObjectAllocator<ArrayBufferObject> {
        static final ObjectAllocator<ArrayBufferObject> INSTANCE = new ArrayBufferObjectAllocator();

        @Override
        public ArrayBufferObject newInstance(Realm realm) {
            return new ArrayBufferObject(realm);
        }
    }

    /**
     * FIXME: spec bug (function CreateByteArrayBlock not defined)
     */
    public static ByteBuffer CreateByteArrayBlock(ExecutionContext cx, double bytes) {
        if (bytes > Integer.MAX_VALUE) {
            throwInternalError(cx, Messages.Key.OutOfMemory);
        }
        try {
            // default byte-order is little-endian
            return ByteBuffer.allocate((int) bytes).order(ByteOrder.LITTLE_ENDIAN);
        } catch (OutOfMemoryError e) {
            throw throwInternalError(cx, Messages.Key.OutOfMemoryVM);
        }
    }

    /**
     * FIXME: spec bug (function CopyBlockElements not defined)
     */
    public static void CopyBlockElements(ByteBuffer fromBuf, double fromPos, ByteBuffer toBuf,
            double toPos, double length) {
        assert length >= 0;
        assert fromPos >= 0 && fromPos + length <= fromBuf.capacity();
        assert toPos >= 0 && toPos + length <= toBuf.capacity();

        fromBuf.limit((int) (fromPos + length));
        fromBuf.position((int) fromPos);
        toBuf.limit((int) (toPos + length));
        toBuf.position((int) toPos);
        toBuf.put(fromBuf);
    }

    /**
     * 24.1.1.1 AllocateArrayBuffer (constructor)
     */
    public static ArrayBufferObject AllocateArrayBuffer(ExecutionContext cx, Intrinsics constructor) {
        return AllocateArrayBuffer(cx, cx.getIntrinsic(constructor));
    }

    /**
     * 24.1.1.1 AllocateArrayBuffer (constructor)
     */
    public static ArrayBufferObject AllocateArrayBuffer(ExecutionContext cx, Object constructor) {
        /* steps 1-2 */
        ArrayBufferObject obj = OrdinaryCreateFromConstructor(cx, constructor,
                Intrinsics.ArrayBufferPrototype, ArrayBufferObjectAllocator.INSTANCE);
        /* step 3 */
        obj.setByteLength(0);
        /* step 4 */
        return obj;
    }

    /**
     * 24.1.1.2 SetArrayBufferData (arrayBuffer, bytes)
     */
    public static ArrayBufferObject SetArrayBufferData(ExecutionContext cx,
            ArrayBufferObject arrayBuffer, double bytes) {
        /* step 1 (implicit) */
        /* step 2 */
        assert bytes >= 0;
        /* steps 3-4 */
        ByteBuffer block = CreateByteArrayBlock(cx, bytes);
        /* step 5 */
        arrayBuffer.setData(block);
        /* step 6 */
        arrayBuffer.setByteLength((long) bytes);
        /* step 7 */
        return arrayBuffer;
    }

    /**
     * 24.1.1.3 CloneArrayBuffer (srcBuffer, srcByteOffset, srcType, cloneElementType, srcLength)
     */
    public static ArrayBufferObject CloneArrayBuffer(ExecutionContext cx,
            ArrayBufferObject srcData, long srcByteOffset, ElementType srcType,
            ElementType cloneElementType, long srcLength) {
        assert srcByteOffset >= 0
                && (srcByteOffset <= srcLength * srcType.size() || srcLength == 0) : "startByteIndex="
                + srcByteOffset + ", length=" + srcLength + ", srcType.size=" + srcType.size();
        assert srcByteOffset % srcType.size() == 0;

        ArrayBufferObject destData = AllocateArrayBuffer(cx, Intrinsics.ArrayBuffer);
        SetArrayBufferData(cx, destData, srcLength * cloneElementType.size());

        for (long index = 0; index < srcLength; ++index) {
            double value = GetValueFromBuffer(cx, srcData, srcByteOffset + index * srcType.size(),
                    srcType);
            SetValueInBuffer(cx, destData, index * cloneElementType.size(), cloneElementType, value);
        }

        return destData;
    }

    /**
     * 24.1.1.4 GetValueFromBuffer (arrayBuffer, byteIndex, type, isLittleEndian)
     */
    public static double GetValueFromBuffer(ExecutionContext cx, ArrayBufferObject arrayBuffer,
            long byteIndex, ElementType type) {
        return GetValueFromBuffer(cx, arrayBuffer, byteIndex, type, IS_LITTLE_ENDIAN);
    }

    /**
     * 24.1.1.4 GetValueFromBuffer (arrayBuffer, byteIndex, type, isLittleEndian)
     */
    public static double GetValueFromBuffer(ExecutionContext cx, ArrayBufferObject arrayBuffer,
            long byteIndex, ElementType type, boolean isLittleEndian) {
        /* steps 1-2 */
        assert (byteIndex >= 0 && (byteIndex + type.size()) <= arrayBuffer.getByteLength());
        /* steps 3-4 */
        ByteBuffer block = arrayBuffer.getData();
        if (block == null) {
            throw throwTypeError(cx, Messages.Key.IncompatibleObject);
        }
        /* steps 7-8 */
        if ((block.order() == ByteOrder.LITTLE_ENDIAN) != isLittleEndian) {
            block.order(isLittleEndian ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
        }

        int index = (int) byteIndex;
        switch (type) {
        case Float32: {
            /* steps 5-6, 9 */
            double rawValue = block.getFloat(index);
            return Double.isNaN(rawValue) ? Double.NaN : rawValue;
        }
        case Float64: {
            /* steps 5-6, 10 */
            double rawValue = block.getDouble(index);
            return Double.isNaN(rawValue) ? Double.NaN : rawValue;
        }

        /* steps 5-6, 11, 13 */
        case Uint8:
        case Uint8C:
            return block.get(index) & 0xffL;
        case Uint16:
            return block.getShort(index) & 0xffffL;
        case Uint32:
            return block.getInt(index) & 0xffffffffL;

            /* steps 5-6, 12-13 */
        case Int8:
            return (long) block.get(index);
        case Int16:
            return (long) block.getShort(index);
        case Int32:
            return (long) block.getInt(index);

        default:
            throw new IllegalStateException();
        }
    }

    /**
     * 24.1.1.5 SetValueInBuffer (arrayBuffer, byteIndex, type, value, isLittleEndian)
     */
    public static void SetValueInBuffer(ExecutionContext cx, ArrayBufferObject arrayBuffer,
            long byteIndex, ElementType type, double value) {
        SetValueInBuffer(cx, arrayBuffer, byteIndex, type, value, IS_LITTLE_ENDIAN);
    }

    /**
     * 24.1.1.5 SetValueInBuffer (arrayBuffer, byteIndex, type, value, isLittleEndian)
     */
    public static void SetValueInBuffer(ExecutionContext cx, ArrayBufferObject arrayBuffer,
            long byteIndex, ElementType type, double value, boolean isLittleEndian) {
        /* steps 1-2 */
        assert (byteIndex >= 0 && (byteIndex + type.size()) <= arrayBuffer.getByteLength());
        /* steps 3-4 */
        ByteBuffer block = arrayBuffer.getData();
        if (block == null) {
            throw throwTypeError(cx, Messages.Key.IncompatibleObject);
        }
        /* steps 7-10 */
        if ((block.order() == ByteOrder.LITTLE_ENDIAN) != isLittleEndian) {
            block.order(isLittleEndian ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
        }

        int index = (int) byteIndex;
        switch (type) {
        case Float32:
            /* steps 8, 11-12 */
            block.putFloat(index, (float) value);
            return;
        case Float64:
            /* steps 9, 11-12 */
            block.putDouble(index, value);
            return;

            /* steps 10-12 */
        case Int8:
            block.put(index, ElementType.ToInt8(value));
            return;
        case Uint8:
            block.put(index, ElementType.ToUint8(value));
            return;
        case Uint8C:
            block.put(index, ElementType.ToUint8Clamp(value));
            return;

        case Int16:
            block.putShort(index, ElementType.ToInt16(value));
            return;
        case Uint16:
            block.putShort(index, ElementType.ToUint16(value));
            return;

        case Int32:
            block.putInt(index, ElementType.ToInt32(value));
            return;
        case Uint32:
            block.putInt(index, ElementType.ToUint32(value));
            return;

        default:
            throw new IllegalStateException();
        }
    }

    /**
     * 24.1.2.1 ArrayBuffer(length)
     */
    @Override
    public Object call(ExecutionContext callerContext, Object thisValue, Object... args) {
        ExecutionContext calleeContext = calleeContext();
        Object length = args.length > 0 ? args[0] : UNDEFINED;
        /* step 1 (omitted) */
        /* step 2 */
        if (!(thisValue instanceof ArrayBufferObject)) {
            throwTypeError(calleeContext, Messages.Key.IncompatibleObject);
        }
        ArrayBufferObject buf = (ArrayBufferObject) thisValue;
        if (buf.getData() != null) {
            throwTypeError(calleeContext, Messages.Key.IncompatibleObject);
        }
        // FIXME: spec issue? - undefined length is same as 0 for bwcompat?
        if (Type.isUndefined(length)) {
            length = 0;
        }
        /* step 3 */
        double numberLength = ToNumber(calleeContext, length);
        /* steps 4-5 */
        double byteLength = ToInteger(numberLength);
        /* step 6 */
        if (numberLength != byteLength || byteLength < 0) {
            throwRangeError(calleeContext, Messages.Key.InvalidBufferSize);
        }
        /* step 7 */
        return SetArrayBufferData(calleeContext, buf, (long) byteLength);
    }

    /**
     * 24.1.2.2 new ArrayBuffer(...argumentsList)
     */
    @Override
    public ScriptObject construct(ExecutionContext callerContext, Object... args) {
        return OrdinaryConstruct(callerContext, this, args);
    }

    /**
     * 24.1.3 Properties of the ArrayBuffer Constructor
     */
    public enum Properties {
        ;

        @Prototype
        public static final Intrinsics __proto__ = Intrinsics.FunctionPrototype;

        @Value(name = "length", attributes = @Attributes(writable = false, enumerable = false,
                configurable = true))
        public static final int length = 1;

        @Value(name = "name", attributes = @Attributes(writable = false, enumerable = false,
                configurable = false))
        public static final String name = "ArrayBuffer";

        /**
         * 24.1.3.2 ArrayBuffer.prototype
         */
        @Value(name = "prototype", attributes = @Attributes(writable = false, enumerable = false,
                configurable = false))
        public static final Intrinsics prototype = Intrinsics.ArrayBufferPrototype;

        /**
         * 24.1.3.1 ArrayBuffer.isView ( arg )
         */
        @Function(name = "isView", arity = 1)
        public static Object isView(ExecutionContext cx, Object thisValue, Object arg) {
            /* step 1 */
            if (!Type.isObject(arg)) {
                return false;
            }
            /* step 2 */
            if (arg instanceof ArrayBufferView) {
                return true;
            }
            /* step 3 */
            return false;
        }

        /**
         * 24.1.3.3 @@create ( )
         */
        @Function(name = "@@create", symbol = BuiltinSymbol.create, arity = 0,
                attributes = @Attributes(writable = false, enumerable = false, configurable = true))
        public static Object create(ExecutionContext cx, Object thisValue) {
            return AllocateArrayBuffer(cx, thisValue);
        }
    }
}
