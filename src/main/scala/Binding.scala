package monixbinding

import monix.reactive.subjects.BehaviorSubject
import monix.reactive.Observable
import xml.{Node => XmlNode, _}
import org.scalajs.dom.raw.{Node => DomNode}
import org.scalajs.dom

sealed abstract class Binding[A] extends Elem(null, "BINDING", Null, TopScope, true) {
  protected[monixbinding] def observable: Observable[A]

  def map[B](f: A => B): Binding[B]              = Binding(observable.map(f))
  def filter(f: A => Boolean): Binding[A]        = Binding(observable.filter(f))
  def flatMap[B](f: A => Binding[B]): Binding[B] = Binding(observable.flatMap(x => f(x).observable))

  override def toString: String = "<error>"
}

object Binding {
  def apply[T](o: Observable[T]): Binding[T] = new Binding[T] { def observable = o }
}

final case class Var[A](initialValue: A) extends Binding[A] {
  protected[monixbinding] val undelying: BehaviorSubject[A] = BehaviorSubject(initialValue)
  protected[monixbinding] val observable: Observable[A] = undelying

  def :=(newValue: A): Unit = { undelying.onNext(newValue); () }
}

object mount {
  /** Side-effectly mounts an `xml.Node | Bindings` tree on an actual `org.scalajs.dom.raw.Node`. */
  def apply(parent: DomNode, child: XmlNode): Unit =
    mount0(parent, child, None)

  def mount0(parent: DomNode, child: XmlNode, replacingFor: Option[Observable[_]]): Unit =
    child match {
      case b: Binding[_] =>
        b.foreach {
          case n: XmlNode =>
            mount0(parent, n, Some(b.observable))
          case n =>
            val e = s"Value $n should be wrapped in a single XML Node for binding."
            throw new IllegalArgumentException(e)
        }

      case e @ Elem(_, label, metadata, _, child @ _*) =>
        val elemNode = dom.document.createElement(label)
        child.foreach(c => mount0(elemNode, c, None))
        mountMaybeReplacing(parent, elemNode, replacingFor)

      case Text(s: String) =>
        val textNode = dom.document.createTextNode(s)
        mountMaybeReplacing(parent, textNode, replacingFor)

      case _ =>
        val e = "You just found a XML Node whith is neither Text nor Elem, I'm sorry."
        throw new IllegalArgumentException(e)
    }

  def mountMaybeReplacing(parent: DomNode, child: DomNode, replacingFor: Option[Observable[_]]): Unit =
    replacingFor match {
      case None    => parent.appendChild(child); ()
      case Some(o) =>
        // TODO: Check in some global mutable map / the dom itself for an existing node binded to `o`)
        // parent.replaceChilde(oldNode, elemNode)
        ???
    }
}
