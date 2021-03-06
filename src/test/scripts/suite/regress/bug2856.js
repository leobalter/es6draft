/*
 * Copyright (c) 2012-2015 André Bargull
 * Alle Rechte vorbehalten / All Rights Reserved.  Use is subject to license terms.
 *
 * <https://github.com/anba/es6draft>
 */
const {
  assertSame, assertUndefined
} = Assert;

// 9.2.8 AddRestrictedFunctionProperties: Use %ThrowTypeError% from function's [[Realm]]?
// https://bugs.ecmascript.org/show_bug.cgi?id=2856

{
  let r = new Reflect.Realm()
  let f = r.eval("function f() {'use strict'} f")
  let clone = Function.prototype.toMethod.call(f, {})
  assertSame(Object.getPrototypeOf(f), Object.getPrototypeOf(clone));

  let foreignThrower = Object.getOwnPropertyDescriptor(f, "caller");
  let cloneThrower = Object.getOwnPropertyDescriptor(clone, "caller");
  assertUndefined(foreignThrower);
  assertUndefined(cloneThrower);
}
