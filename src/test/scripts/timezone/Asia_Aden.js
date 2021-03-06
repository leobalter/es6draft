/*
 * Copyright (c) 2012-2015 André Bargull
 * Alle Rechte vorbehalten / All Rights Reserved.  Use is subject to license terms.
 *
 * <https://github.com/anba/es6draft>
 */
// Asia/Aden had local mean time (LMT) until 31. Dec. 1949.

{
  let local = new DateTime.Local(1949, Month.December, 31, DayOfWeek.Saturday, 20, 0, 0);
  let utc = new DateTime.UTC(1949, Month.December, 31, DayOfWeek.Saturday, 17, 0, 0);

  assertDate(local, utc, TimeZone(+3), {});
}

{
  let local = new DateTime.Local(1949, Month.December, 31, DayOfWeek.Saturday, 21, 0, 0);
  let utc = new DateTime.UTC(1949, Month.December, 31, DayOfWeek.Saturday, 18, 0, 0);

  assertDate(local, utc, TimeZone(+3), {});
}

{
  let local = new DateTime.Local(1949, Month.December, 31, DayOfWeek.Saturday, 22, 0, 0);
  let utc = new DateTime.UTC(1949, Month.December, 31, DayOfWeek.Saturday, 19, 0, 0);

  assertDate(local, utc, TimeZone(+3), {});
}

{
  let local = new DateTime.Local(1949, Month.December, 31, DayOfWeek.Saturday, 23, 0, 0);
  let utc = new DateTime.UTC(1949, Month.December, 31, DayOfWeek.Saturday, 20, 0, 0);

  assertDate(local, utc, TimeZone(+3), {});
}

{
  let local = new DateTime.Local(1950, Month.January, 1, DayOfWeek.Sunday, 0, 0, 0);
  let utc = new DateTime.UTC(1949, Month.December, 31, DayOfWeek.Saturday, 21, 0, 0);

  assertDate(local, utc, TimeZone(+3), {});
}

{
  let local = new DateTime.Local(1950, Month.January, 1, DayOfWeek.Sunday, 1, 0, 0);
  let utc = new DateTime.UTC(1949, Month.December, 31, DayOfWeek.Saturday, 22, 0, 0);

  assertDate(local, utc, TimeZone(+3), {});
}

{
  let local = new DateTime.Local(1950, Month.January, 1, DayOfWeek.Sunday, 2, 0, 0);
  let utc = new DateTime.UTC(1949, Month.December, 31, DayOfWeek.Saturday, 23, 0, 0);

  assertDate(local, utc, TimeZone(+3), {});
}

{
  let local = new DateTime.Local(1950, Month.January, 1, DayOfWeek.Sunday, 3, 0, 0);
  let utc = new DateTime.UTC(1950, Month.January, 1, DayOfWeek.Sunday, 0, 0, 0);

  assertDate(local, utc, TimeZone(+3), {});
}