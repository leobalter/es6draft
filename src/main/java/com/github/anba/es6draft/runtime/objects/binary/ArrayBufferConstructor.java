/**
 * Copyright (c) 2012-2015 André Bargull
 * Alle Rechte vorbehalten / All Rights Reserved.  Use is subject to license terms.
 *
 * <https://github.com/anba/es6draft>
 */
package com.github.anba.es6draft.runtime.objects.binary;

import static com.github.anba.es6draft.runtime.AbstractOperations.*;
import static com.github.anba.es6draft.runtime.internal.Errors.newRangeError;
import static com.github.anba.es6draft.runtime.internal.Errors.newTypeError;
import static com.github.anba.es6draft.runtime.internal.Properties.createProperties;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.github.anba.es6draft.runtime.ExecutionContext;
import com.github.anba.es6draft.runtime.Realm;
import com.github.anba.es6draft.runtime.internal.CompatibilityOption;
import com.github.anba.es6draft.runtime.internal.Initializable;
import com.github.anba.es6draft.runtime.internal.Messages;
import com.github.anba.es6draft.runtime.internal.Properties.Accessor;
import com.github.anba.es6draft.runtime.internal.Properties.Attributes;
import com.github.anba.es6draft.runtime.internal.Properties.CompatibilityExtension;
import com.github.anba.es6draft.runtime.internal.Properties.Function;
import com.github.anba.es6draft.runtime.internal.Properties.Prototype;
import com.github.anba.es6draft.runtime.internal.Properties.Value;
import com.github.anba.es6draft.runtime.types.BuiltinSymbol;
import com.github.anba.es6draft.runtime.types.Constructor;
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
public final class ArrayBufferConstructor extends BuiltinConstructor implements Initializable {
    // set default byte-order to little-endian - implementation specific choice
    private static final ByteOrder DEFAULT_BYTE_ORDER = ByteOrder.LITTLE_ENDIAN;
    private static final boolean IS_LITTLE_ENDIAN = true;
    static {
        assert IS_LITTLE_ENDIAN == (DEFAULT_BYTE_ORDER == ByteOrder.LITTLE_ENDIAN);
    }

    /**
     * Constructs a new ArrayBuffer constructor function.
     * 
     * @param realm
     *            the realm object
     */
    public ArrayBufferConstructor(Realm realm) {
        super(realm, "ArrayBuffer", 1);
    }

    @Override
    public void initialize(Realm realm) {
        createProperties(realm, this, Properties.class);
        createProperties(realm, this, AdditionalProperties.class);
    }

    @Override
    public ArrayBufferConstructor clone() {
        return new ArrayBufferConstructor(getRealm());
    }

    /**
     * 6.2.6.1 CreateByteDataBlock(size)
     * 
     * @param cx
     *            the execution context
     * @param size
     *            the byte buffer size in bytes
     * @return the new byte buffer
     */
    public static ByteBuffer CreateByteDataBlock(ExecutionContext cx, long size) {
        /* step 1 */
        assert size >= 0;
        /* step 2 */
        if (size > Integer.MAX_VALUE) {
            throw newRangeError(cx, Messages.Key.OutOfMemory);
        }
        try {
            /* step 3 */
            // TODO: Call allocateDirect() if size exceeds predefined limit?
            return ByteBuffer.allocate((int) size).order(DEFAULT_BYTE_ORDER);
        } catch (OutOfMemoryError e) {
            /* step 2 */
            throw newRangeError(cx, Messages.Key.OutOfMemoryVM);
        }
    }

    /**
     * 6.2.6.2 CopyDataBlockBytes(toBlock, toIndex, fromBlock, fromIndex, count)
     * 
     * @param toBlock
     *            the target byte buffer
     * @param toIndex
     *            the target offset
     * @param fromBlock
     *            the source byte buffer
     * @param fromIndex
     *            the source offset
     * @param count
     *            the number of bytes to copy
     */
    public static void CopyDataBlockBytes(ByteBuffer toBlock, long toIndex, ByteBuffer fromBlock,
            long fromIndex, long count) {
        /* step 1 */
        assert fromBlock != toBlock;
        /* step 2 */
        assert fromIndex >= 0 && toIndex >= 0 && count >= 0;
        /* steps 3-4 */
        assert fromIndex + count <= fromBlock.capacity();
        /* steps 5-6 */
        assert toIndex + count <= toBlock.capacity();

        /* steps 7-8 */
        fromBlock.limit((int) (fromIndex + count)).position((int) fromIndex);
        toBlock.limit((int) (toIndex + count)).position((int) toIndex);
        toBlock.put(fromBlock);
        toBlock.clear();
        fromBlock.clear();
    }

    /**
     * 24.1.1.1 AllocateArrayBuffer( constructor, byteLength )
     * 
     * @param cx
     *            the execution context
     * @param constructor
     *            the constructor function
     * @param byteLength
     *            the buffer byte length
     * @return the new array buffer object
     */
    public static ArrayBufferObject AllocateArrayBuffer(ExecutionContext cx,
            Constructor constructor, long byteLength) {
        /* steps 1-2 */
        ScriptObject proto = GetPrototypeFromConstructor(cx, constructor,
                Intrinsics.ArrayBufferPrototype);
        /* step 3 */
        assert byteLength >= 0;
        /* steps 4-5 */
        ByteBuffer block = CreateByteDataBlock(cx, byteLength);
        /* steps 1-2, 6-8 */
        return new ArrayBufferObject(cx.getRealm(), block, byteLength, proto);
    }

    /**
     * 24.1.1.2 IsDetachedBuffer( arrayBuffer )
     * 
     * @param arrayBuffer
     *            the array buffer object
     * @return {@code true} if the array buffer is detached
     */
    public static boolean IsDetachedBuffer(ArrayBufferObject arrayBuffer) {
        /* step 1 (not applicable) */
        /* steps 2-3 */
        return arrayBuffer.isDetached();
    }

    /**
     * 24.1.1.3 DetachArrayBuffer( arrayBuffer )
     * 
     * @param cx
     *            the execution context
     * @param arrayBuffer
     *            the array buffer object
     */
    public static void DetachArrayBuffer(ExecutionContext cx, ArrayBufferObject arrayBuffer) {
        // TODO: Perform any checks here? E.g. already detached?
        /* step 1 (not applicable) */
        /* steps 2-3 */
        arrayBuffer.detach();
        /* step 4 (return) */
    }

    /**
     * 24.1.1.4 CloneArrayBuffer (srcBuffer, srcByteOffset)
     * 
     * @param cx
     *            the execution context
     * @param srcBuffer
     *            the source buffer
     * @param srcByteOffset
     *            the source offset
     * @return the new array buffer object
     */
    public static ArrayBufferObject CloneArrayBuffer(ExecutionContext cx,
            ArrayBufferObject srcBuffer, long srcByteOffset) {
        return CloneArrayBuffer(cx, srcBuffer, srcByteOffset, null);
    }

    /**
     * 24.1.1.4 CloneArrayBuffer (srcBuffer, srcByteOffset)
     * 
     * @param cx
     *            the execution context
     * @param srcBuffer
     *            the source buffer
     * @param srcByteOffset
     *            the source offset
     * @param cloneConstructor
     *            the intrinsic constructor function
     * @return the new array buffer object
     */
    public static ArrayBufferObject CloneArrayBuffer(ExecutionContext cx,
            ArrayBufferObject srcBuffer, long srcByteOffset, Intrinsics cloneConstructor) {
        /* step 1 (implicit) */
        /* steps 2-3 */
        Constructor bufferConstructor;
        if (cloneConstructor == null) {
            /* steps 2.a-b */
            bufferConstructor = SpeciesConstructor(cx, srcBuffer, Intrinsics.ArrayBuffer);
            /* step 2.c */
            if (IsDetachedBuffer(srcBuffer)) {
                throw newTypeError(cx, Messages.Key.BufferDetached);
            }
        } else {
            /* step 3 */
            assert IsConstructor(cx.getIntrinsic(cloneConstructor));
            bufferConstructor = (Constructor) cx.getIntrinsic(cloneConstructor);
        }
        /* step 4 */
        ByteBuffer srcBlock = srcBuffer.getData();
        /* step 5 */
        long srcLength = srcBuffer.getByteLength();
        /* step 6 */
        assert srcByteOffset <= srcLength;
        /* step 7 */
        long cloneLength = srcLength - srcByteOffset;
        /* steps 8-9 */
        ArrayBufferObject targetBuffer = AllocateArrayBuffer(cx, bufferConstructor, cloneLength);
        /* step 10 */
        if (IsDetachedBuffer(srcBuffer)) {
            throw newTypeError(cx, Messages.Key.BufferDetached);
        }
        /* step 11 */
        ByteBuffer targetBlock = targetBuffer.getData();
        /* step 12 */
        CopyDataBlockBytes(targetBlock, 0, srcBlock, srcByteOffset, cloneLength);
        /* step 13 */
        return targetBuffer;
    }

    /**
     * 24.1.1.5 GetValueFromBuffer (arrayBuffer, byteIndex, type, isLittleEndian)
     * 
     * @param arrayBuffer
     *            the array buffer object
     * @param byteIndex
     *            the byte index
     * @param type
     *            the element type
     * @return the buffer value
     */
    public static double GetValueFromBuffer(ArrayBufferObject arrayBuffer, long byteIndex,
            ElementType type) {
        return GetValueFromBuffer(arrayBuffer, byteIndex, type, IS_LITTLE_ENDIAN);
    }

    /**
     * 24.1.1.5 GetValueFromBuffer (arrayBuffer, byteIndex, type, isLittleEndian)
     * 
     * @param arrayBuffer
     *            the array buffer object
     * @param byteIndex
     *            the byte index
     * @param type
     *            the element type
     * @param isLittleEndian
     *            the little endian flag
     * @return the buffer value
     */
    public static double GetValueFromBuffer(ArrayBufferObject arrayBuffer, long byteIndex,
            ElementType type, boolean isLittleEndian) {
        /* step 1 */
        assert !IsDetachedBuffer(arrayBuffer) : "ArrayBuffer is detached";
        /* steps 2-3 */
        assert byteIndex >= 0 && (byteIndex + type.size() <= arrayBuffer.getByteLength());
        /* step 4 */
        ByteBuffer block = arrayBuffer.getData();
        /* steps 7-8 */
        if ((block.order() == ByteOrder.LITTLE_ENDIAN) != isLittleEndian) {
            // NB: Byte order is not reset after this call.
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
            throw new AssertionError();
        }
    }

    /**
     * 24.1.1.6 SetValueInBuffer (arrayBuffer, byteIndex, type, value, isLittleEndian)
     * 
     * @param arrayBuffer
     *            the array buffer object
     * @param byteIndex
     *            the byte index
     * @param type
     *            the element type
     * @param value
     *            the new element value
     */
    public static void SetValueInBuffer(ArrayBufferObject arrayBuffer, long byteIndex,
            ElementType type, double value) {
        SetValueInBuffer(arrayBuffer, byteIndex, type, value, IS_LITTLE_ENDIAN);
    }

    /**
     * 24.1.1.6 SetValueInBuffer (arrayBuffer, byteIndex, type, value, isLittleEndian)
     * 
     * @param arrayBuffer
     *            the array buffer object
     * @param byteIndex
     *            the byte index
     * @param type
     *            the element type
     * @param value
     *            the new element value
     * @param isLittleEndian
     *            the little endian flag
     */
    public static void SetValueInBuffer(ArrayBufferObject arrayBuffer, long byteIndex,
            ElementType type, double value, boolean isLittleEndian) {
        /* step 1 */
        assert !IsDetachedBuffer(arrayBuffer) : "ArrayBuffer is detached";
        /* steps 2-3 */
        assert byteIndex >= 0 && (byteIndex + type.size() <= arrayBuffer.getByteLength());
        /* step 4 (not applicable) */
        /* step 5 */
        ByteBuffer block = arrayBuffer.getData();
        /* step 6 */
        assert block != null;
        /* step 8 */
        if ((block.order() == ByteOrder.LITTLE_ENDIAN) != isLittleEndian) {
            // NB: Byte order is not reset after this call.
            block.order(isLittleEndian ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
        }

        int index = (int) byteIndex;
        switch (type) {
        case Float32:
            /* steps 5, 9, 12-13 */
            block.putFloat(index, (float) value);
            return;
        case Float64:
            /* steps 5, 10, 12-13 */
            block.putDouble(index, value);
            return;

            /* steps 5, 11-13 */
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
            throw new AssertionError();
        }
    }

    /**
     * 24.1.2.1 ArrayBuffer(length)
     */
    @Override
    public ArrayBufferObject call(ExecutionContext callerContext, Object thisValue, Object... args) {
        /* step 1 */
        throw newTypeError(calleeContext(), Messages.Key.InvalidCall, "ArrayBuffer");
    }

    /**
     * 24.1.2.1 ArrayBuffer(length)
     */
    @Override
    public ArrayBufferObject construct(ExecutionContext callerContext, Constructor newTarget,
            Object... args) {
        ExecutionContext calleeContext = calleeContext();
        Object length = argument(args, 0);
        /* step 1 (not applicable) */
        // FIXME: spec issue? - missing length parameter same as 0 for bwcompat?
        if (args.length == 0
                && getRealm().isEnabled(CompatibilityOption.ArrayBufferMissingLength)) {
            length = 0;
        }
        /* step 2 */
        double numberLength = ToNumber(calleeContext, length);
        /* steps 3-4 */
        long byteLength = ToLength(numberLength);
        /* step 5 */
        if (numberLength != byteLength) { // SameValueZero
            throw newRangeError(calleeContext, Messages.Key.InvalidBufferSize);
        }
        /* step 6 */
        return AllocateArrayBuffer(calleeContext, newTarget, byteLength);
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
                configurable = true))
        public static final String name = "ArrayBuffer";

        /**
         * 24.1.3.2 ArrayBuffer.prototype
         */
        @Value(name = "prototype", attributes = @Attributes(writable = false, enumerable = false,
                configurable = false))
        public static final Intrinsics prototype = Intrinsics.ArrayBufferPrototype;

        /**
         * 24.1.3.1 ArrayBuffer.isView ( arg )
         * 
         * @param cx
         *            the execution context
         * @param thisValue
         *            the function this-value
         * @param arg
         *            the argument object
         * @return {@code true} if the argument is an array buffer view object
         */
        @Function(name = "isView", arity = 1)
        public static Object isView(ExecutionContext cx, Object thisValue, Object arg) {
            /* steps 1-3 */
            return arg instanceof ArrayBufferView;
        }

        /**
         * 24.1.3.3 get ArrayBuffer [ @@species ]
         * 
         * @param cx
         *            the execution context
         * @param thisValue
         *            the function this-value
         * @return the species object
         */
        @Accessor(name = "get [Symbol.species]", symbol = BuiltinSymbol.species,
                type = Accessor.Type.Getter)
        public static Object species(ExecutionContext cx, Object thisValue) {
            /* step 1 */
            return thisValue;
        }
    }

    /**
     * Proposed ECMAScript 7 additions
     */
    @CompatibilityExtension(CompatibilityOption.ArrayBufferTransfer)
    public enum AdditionalProperties {
        ;

        private static ArrayBufferObject thisArrayBufferObjectChecked(ExecutionContext cx, Object m) {
            if (m instanceof ArrayBufferObject) {
                ArrayBufferObject buffer = (ArrayBufferObject) m;
                if (IsDetachedBuffer(buffer)) {
                    throw newTypeError(cx, Messages.Key.BufferDetached);
                }
                return buffer;
            }
            throw newTypeError(cx, Messages.Key.IncompatibleObject);
        }

        /**
         * ArrayBuffer.transfer(oldBuffer [, newByteLength])
         * 
         * @param cx
         *            the execution context
         * @param thisValue
         *            the function this-value
         * @param oldBuffer
         *            the old array buffer
         * @param newByteLength
         *            the optional new byte length
         * @return the result index
         */
        @Function(name = "transfer", arity = 1)
        public static Object transfer(ExecutionContext cx, Object thisValue, Object oldBuffer,
                Object newByteLength) {
            ArrayBufferObject oldArrayBuffer = thisArrayBufferObjectChecked(cx, oldBuffer);
            // newByteLength defaults to oldBuffer.byteLength
            if (Type.isUndefined(newByteLength)) {
                newByteLength = oldArrayBuffer.getByteLength();
            }
            // Perform length same validation as in new ArrayBuffer(length).
            double numberLength = ToNumber(cx, newByteLength);
            long byteLength = ToLength(numberLength);
            if (numberLength != byteLength) { // SameValueZero
                throw newRangeError(cx, Messages.Key.InvalidBufferSize);
            }
            // Create new array buffer from @@species like in CloneArrayBuffer().
            Constructor ctor = SpeciesConstructor(cx, oldArrayBuffer, Intrinsics.ArrayBuffer);
            ScriptObject proto = GetPrototypeFromConstructor(cx, ctor,
                    Intrinsics.ArrayBufferPrototype);
            if (IsDetachedBuffer(oldArrayBuffer)) {
                throw newTypeError(cx, Messages.Key.BufferDetached);
            }
            // Grab old array buffer data and call detach.
            ByteBuffer oldData = oldArrayBuffer.getData();
            long oldByteLength = oldArrayBuffer.getByteLength();
            DetachArrayBuffer(cx, oldArrayBuffer);
            // Extend byte buffer.
            if (byteLength > oldByteLength) {
                ByteBuffer newData = CreateByteDataBlock(cx, byteLength);
                CopyDataBlockBytes(newData, 0, oldData, 0, oldByteLength);
                return new ArrayBufferObject(cx.getRealm(), newData, byteLength, proto);
            }
            // Truncate byte buffer.
            if (byteLength < oldByteLength) {
                // Possible improvement: Use limit() or slice() to reduce buffer allocations.
                ByteBuffer newData = CreateByteDataBlock(cx, byteLength);
                CopyDataBlockBytes(newData, 0, oldData, 0, byteLength);
                return new ArrayBufferObject(cx.getRealm(), newData, byteLength, proto);
            }
            // Create new array buffer with the same byte buffer if length did not change.
            return new ArrayBufferObject(cx.getRealm(), oldData, byteLength, proto);
        }
    }
}
