package com.rethinkscala

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility
import com.fasterxml.jackson.annotation.JsonInclude.Include
import com.fasterxml.jackson.annotation.PropertyAccessor
import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.rethinkscala.ast._
import com.rethinkscala.backend.{Connection => BackendConnection}
import com.rethinkscala.reflect.Reflector

import scala.collection.mutable.ArrayBuffer

/**
  * Created with IntelliJ IDEA.
  * User: keyston
  * Date: 7/13/13
  * Time: 11:00 AM
  *
  */


object CurrentSchema {


  private var _current: Option[Schema] = None

  def apply(o: Option[Schema]) = _current = o


  def getOrElse(s: Schema) = _current.getOrElse(s)


}

class Schema extends Helpers {


  protected implicit def thisSchema = this


  def defaultConnection: Option[BackendConnection] = None


  protected def defineMapper = {
    val mapper = new ObjectMapper()
    mapper.registerModule(DefaultScalaModule)

    mapper.setSerializationInclusion(Include.NON_NULL)
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    mapper.setVisibility(PropertyAccessor.FIELD, Visibility.PUBLIC_ONLY)

    mapper
  }

  Reflector.mapper = defineMapper


  def get[T <: Document](implicit mf: Manifest[T]): Option[Table[T]] = _tableTypes get (mf.runtimeClass) map {
    t => t.asInstanceOf[Table[T]]
  }

  def lift[T <: Document](f: PartialFunction[Table[T], Unit])(implicit mf: Manifest[T]): Unit = get[T] map (f(_))

  def liftAs[T <: Document, R](f: PartialFunction[Table[T], R])(implicit mf: Manifest[T]): Option[R] = get[T] map (f(_))


  implicit def doc2Active[A <: Document](a: A)(implicit extractor: ResultExtractor[A], c: BackendConnection): ActiveRecord[A] =
    new ActiveRecord(a)

  class ActiveRecord[T <: Document](o: T)(implicit c: BackendConnection, extractor: ResultExtractor[T]) {
    private def performAction[R](action: (Table[T]) => Produce[R])(implicit mf: Manifest[R]): Either[Exception, R] = {

      val m = extractor.manifest
      implicit val extractor2 = extractor.to[R]
      val result = CurrentSchema.getOrElse(thisSchema)._tableTypes get (m.runtimeClass) map {
        table: Table[_] => block(action(table.asInstanceOf[Table[T]]))

      }

      result getOrElse (Left(new Exception(s"No Table found in Schema for this ${m.runtimeClass}")))
    }

    /**
      * Same as {{{table.insert(a)}}}
      */
    def save: Either[Exception, InsertResult] =
      performAction(_.insert(o).withChanges)

    /**
      * Same as {{{table.update(a)}}}
      */
    def replace: Either[Exception, ChangeResult] =

      performAction(_.get(o.asInstanceOf[ {val id: Any}].id match {
        case Some(v: Any) => Expr(v).asInstanceOf[Typed]
        case v: Any => Expr(v).asInstanceOf[Typed]
      }).replace(o))

    // def save: Option[T]
  }

  private val _tableTypes = new collection.mutable.HashMap[Class[_], Table[_]]

  private[rethinkscala] def _addTableType[T <: Document](typeT: Class[_], t: Table[T]) =
    _tableTypes += ((typeT, t))

  private val tables = new ArrayBuffer[Table[_]]

  def tableNameFromClass(c: Class[_]): String =
    c.getSimpleName.replaceAll("^([^A-Za-z_])", "_$1").replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2").replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase

  protected def table[T <: Document](implicit manifestT: Manifest[T]): Table[T] =
    table(tableNameFromClass(manifestT.runtimeClass))(manifestT)


  @deprecated("use table(String,ReadMode.Kind,Option[String])","0.4.8")
  protected def table[T <: Document](name: String, useOutDated: Option[Boolean], db: Option[String]
                                    )(implicit manifestT: Manifest[T]): Table[T] = {


    table[T](name, ReadMode.Single, db)(manifestT)
  }

  protected def table[T <: Document](name: String, readMode: ReadMode.Kind = ReadMode.Single, db: Option[String] = None
                                    )(implicit manifestT: Manifest[T]): Table[T] = {


    val typeT = manifest.runtimeClass
    val t = new Table[T](name, readMode, db.map(DB))
    tables.append(t)
    _addTableType[T](typeT, t)
    t
  }

  private val _tableViews = ArrayBuffer.empty[TableView[_]]

  def on[T <: Document](table: Table[T])(f: TableView[T] => Unit)(implicit mf: Manifest[T]): Unit = {
    val view = new TableView[T](table)
    f(view)
    _tableViews.append(view)
  }

  def db(name: String) = RethinkApi.db(name)

  /*
  def setup(implicit c: Connection) = {
    block {
      implicit c: BlockingConnection =>
        import c.delegate._
        tables.foreach {
          t => {

            t.db.map(_.create.run match {
              case Left(e) => println(e)
              case Right(v) => println(s"Db ${t.name} created -> $v")
            })
            t.create.run match {
              case Left(e) => println(e)
              case Right(v) => println(s"Table ${t.name} created -> $v")
            }
          }
        }
      //  _tableViews foreach (_.apply)
    }

  } */


}

class TableView[T <: Document](table: Table[T]) extends Helpers {

  private[rethinkscala] val _indexes = ArrayBuffer.empty[ProduceBinary]
  /*
  private[rethinkscala] def apply(implicit c: Connection) = block {
    implicit c: BlockingConnection =>
      import c.delegate._
      _indexes foreach (_.run)
  } */

  def db(name: String) = ???

  def index(name: String) = _indexes += table.indexCreate(name)


  def index(name: String, f: Var => Typed) = ???
}
