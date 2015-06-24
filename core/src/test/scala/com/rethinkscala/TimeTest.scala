package com.rethinkscala

import org.joda.time.format.ISODateTimeFormat
import org.scalatest.FunSuite
import org.joda.time.{DateTimeZone, DateTime}
import Blocking._

/**
 * Created with IntelliJ IDEA.
 * User: keyston
 * Date: 10/4/13
 * Time: 10:56 AM
 *
 */

case class DateTimeSerializer(value: DateTime)

class TimeTest extends FunSuite with WithBase {


  test("r.now") {
    val now = new DateTime(DateTimeZone.UTC)

    assert(r.now.run, {
      dt: DateTime => {

        dt.dayOfMonth().get() == now.dayOfMonth().get() && dt.dayOfWeek().get() == dt.dayOfWeek().get()

      }
    })
  }

  test("time.dayOfWeek") {

    val now = new DateTime(DateTimeZone.UTC)


    val dayOfWeek = r.weekday(now)

    assert(r.now.dayOfWeek.eq(dayOfWeek))
  }

  test("time.day") {

    val now = new DateTime(DateTimeZone.UTC)

    assert(r.now.day.eq(now.getDayOfMonth))
  }
  test("datetime serializer") {


    val now = DateTime.now()
    val iso8601 = ISODateTimeFormat.dateTime().print(now)
    val expectedJson = "{\"value\":[99,[\"" + iso8601 + "\"]]}"
    val json = toJson(r.expr(DateTimeSerializer(now)))
    assert(json.contains(expectedJson))

  }
}
