/*
 * Copyright (c) 2012-2015 André Bargull
 * Alle Rechte vorbehalten / All Rights Reserved.  Use is subject to license terms.
 *
 * <https://github.com/anba/es6draft>
 */
import {assertSame, assertNotUndefined} from "lib/assert.jsm";

// TODO: Replace with Reflect.global when available
const global = System.global;

import "./resources/export_set_global_property1.jsm";
import { /* empty */ } from "./resources/export_set_global_property2.jsm";

assertSame("property1", global.property1);
assertSame("property2", global.property2);
