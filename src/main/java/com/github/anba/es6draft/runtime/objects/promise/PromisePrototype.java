/**
 * Copyright (c) 2012-2015 André Bargull
 * Alle Rechte vorbehalten / All Rights Reserved.  Use is subject to license terms.
 *
 * <https://github.com/anba/es6draft>
 */
package com.github.anba.es6draft.runtime.objects.promise;

import static com.github.anba.es6draft.runtime.AbstractOperations.Invoke;
import static com.github.anba.es6draft.runtime.AbstractOperations.IsCallable;
import static com.github.anba.es6draft.runtime.AbstractOperations.SpeciesConstructor;
import static com.github.anba.es6draft.runtime.internal.Errors.newTypeError;
import static com.github.anba.es6draft.runtime.internal.Properties.createProperties;
import static com.github.anba.es6draft.runtime.objects.promise.PromiseAbstractOperations.IsPromise;
import static com.github.anba.es6draft.runtime.objects.promise.PromiseAbstractOperations.NewPromiseCapability;
import static com.github.anba.es6draft.runtime.types.Undefined.UNDEFINED;

import com.github.anba.es6draft.runtime.ExecutionContext;
import com.github.anba.es6draft.runtime.Realm;
import com.github.anba.es6draft.runtime.internal.Initializable;
import com.github.anba.es6draft.runtime.internal.Messages;
import com.github.anba.es6draft.runtime.internal.Properties.Attributes;
import com.github.anba.es6draft.runtime.internal.Properties.Function;
import com.github.anba.es6draft.runtime.internal.Properties.Prototype;
import com.github.anba.es6draft.runtime.internal.Properties.Value;
import com.github.anba.es6draft.runtime.objects.promise.PromiseAbstractOperations.PromiseReactionTask;
import com.github.anba.es6draft.runtime.types.BuiltinSymbol;
import com.github.anba.es6draft.runtime.types.Callable;
import com.github.anba.es6draft.runtime.types.Constructor;
import com.github.anba.es6draft.runtime.types.Intrinsics;
import com.github.anba.es6draft.runtime.types.ScriptObject;
import com.github.anba.es6draft.runtime.types.builtins.OrdinaryObject;

/**
 * <h1>25 Control Abstraction Objects</h1><br>
 * <h2>25.4 Promise Objects</h2>
 * <ul>
 * <li>25.4.5 Properties of the Promise Prototype Object
 * </ul>
 */
public final class PromisePrototype extends OrdinaryObject implements Initializable {
    /**
     * Constructs a new Promise prototype object.
     * 
     * @param realm
     *            the realm object
     */
    public PromisePrototype(Realm realm) {
        super(realm);
    }

    @Override
    public void initialize(Realm realm) {
        createProperties(realm, this, Properties.class);
    }

    /**
     * 25.4.5 Properties of the Promise Prototype Object
     */
    public enum Properties {
        ;

        @Prototype
        public static final Intrinsics __proto__ = Intrinsics.ObjectPrototype;

        /**
         * 25.4.5.2 Promise.prototype.constructor
         */
        @Value(name = "constructor")
        public static final Intrinsics constructor = Intrinsics.Promise;

        /**
         * 25.4.5.1 Promise.prototype.catch ( onRejected )
         * 
         * @param cx
         *            the execution context
         * @param thisValue
         *            the function this-value
         * @param onRejected
         *            the onRejected handler
         * @return the new promise object
         */
        @Function(name = "catch", arity = 1)
        public static Object _catch(ExecutionContext cx, Object thisValue, Object onRejected) {
            /* step 1 */
            Object promise = thisValue;
            /* step 2 */
            return Invoke(cx, promise, "then", UNDEFINED, onRejected);
        }

        /**
         * 25.4.5.3 Promise.prototype.then ( onFulfilled , onRejected )
         * 
         * @param cx
         *            the execution context
         * @param thisValue
         *            the function this-value
         * @param onFulfilled
         *            the onFulfilled handler
         * @param onRejected
         *            the onRejected handler
         * @return the new promise object
         */
        @Function(name = "then", arity = 2)
        public static Object then(ExecutionContext cx, Object thisValue, Object onFulfilled,
                Object onRejected) {
            /* step 2 */
            if (!IsPromise(thisValue)) {
                throw newTypeError(cx, Messages.Key.IncompatibleObject);
            }
            /* step 1 */
            PromiseObject promise = (PromiseObject) thisValue;
            /* steps 3-4 */
            Constructor c = SpeciesConstructor(cx, promise, Intrinsics.Promise);
            /* steps 5-6 */
            PromiseCapability<ScriptObject> resultCapability = NewPromiseCapability(cx, c);
            /* step 7 */
            return PerformPromiseThen(cx, promise, onFulfilled, onRejected, resultCapability);
        }

        /**
         * 25.4.5.4 Promise.prototype [ @@toStringTag ]
         */
        @Value(name = "[Symbol.toStringTag]", symbol = BuiltinSymbol.toStringTag,
                attributes = @Attributes(writable = false, enumerable = false, configurable = true))
        public static final String toStringTag = "Promise";
    }

    /**
     * 25.4.5.3.1 PerformPromiseThen ( promise, onFulfilled, onRejected, resultCapability )
     * 
     * @param <PROMISE>
     *            the promise type
     * @param cx
     *            the execution context
     * @param promise
     *            the promise object
     * @param onFulfilled
     *            the onFulfilled handler
     * @param onRejected
     *            the onRejected handler
     * @param resultCapability
     *            the new promise capability record
     * @return the new promise object
     */
    public static <PROMISE extends ScriptObject> PROMISE PerformPromiseThen(ExecutionContext cx,
            PromiseObject promise, Object onFulfilled, Object onRejected,
            PromiseCapability<PROMISE> resultCapability) {
        /* steps 1-2 (not applicable) */
        /* step 3 */
        PromiseReaction.Type fulfillType = PromiseReaction.Type.Function;
        if (!IsCallable(onFulfilled)) {
            fulfillType = PromiseReaction.Type.Identity;
            onFulfilled = null;
        }
        /* step 4 */
        PromiseReaction.Type rejectType = PromiseReaction.Type.Function;
        if (!IsCallable(onRejected)) {
            rejectType = PromiseReaction.Type.Thrower;
            onRejected = null;
        }
        /* step 5 */
        PromiseReaction fulfillReaction = new PromiseReaction(resultCapability,
                (Callable) onFulfilled, fulfillType);
        /* step 6 */
        PromiseReaction rejectReaction = new PromiseReaction(resultCapability,
                (Callable) onRejected, rejectType);
        /* step 7 */
        if (promise.getState() == PromiseObject.State.Pending) {
            promise.addFulfillReaction(fulfillReaction);
            promise.addRejectReaction(rejectReaction);
            promise.notifyRejectReaction(rejectReaction);
        }
        /* step 8 */
        else if (promise.getState() == PromiseObject.State.Fulfilled) {
            Object value = promise.getResult();
            Realm realm = cx.getRealm();
            realm.enqueuePromiseTask(new PromiseReactionTask(realm, fulfillReaction, value));
        }
        /* step 9 */
        else {
            assert promise.getState() == PromiseObject.State.Rejected;
            Object reason = promise.getResult();
            Realm realm = cx.getRealm();
            realm.enqueuePromiseTask(new PromiseReactionTask(realm, rejectReaction, reason));
            promise.notifyRejectReaction(rejectReaction);
        }
        /* step 10 */
        return resultCapability.getPromise();
    }
}
