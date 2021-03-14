package mhtml.scalatags

import mhtml.{Cancelable, Rx}
import org.scalajs.dom
import org.scalajs.dom.raw.Node
import scalatags.JsDom.all.{Frag, s}
import scalatags.Text.all._
import scalatags.generic

import java.util.Objects
import scala.collection.mutable
import scala.scalajs.js

object mount {

  trait Cancelables {
    def register(c: Cancelable): Cancelable
    def cancelAll(): Unit
  }

  class BufferCancelable extends Cancelables {
    val buffer: mutable.Set[Cancelable] = mutable.Set.empty[Cancelable]

    override def cancelAll: Unit = buffer.foreach(_.cancel())
    def register(c: Cancelable): Cancelable = {
      buffer += c
      Cancelable(() => buffer -= c)
    }
  }

  implicit def bindRx(implicit cancelables: Cancelables) =
    new generic.AttrValue[dom.Element, Rx[String]]{
      def apply(t: dom.Element, a: generic.Attr, rx: Rx[String]): Unit = {
        val c = rx.impure.run { value =>
          t.setAttribute(a.name, value)
        }
        cancelables.register(c)
      }
    }

  implicit class RxFrag[A](rx: Rx[A])(implicit ev: A => Frag, cancelables: Cancelables) extends Frag {
    Objects.requireNonNull(rx)
    def applyTo(t: dom.Element): Unit = {
      val (start, end) = t.createMountSection()
      val c1 = rx.impure.run { a =>
        t.cleanMountSection(start, end)
        t.mountHere(a.render, Some(start))
      }
      cancelables.register(c1)
    }

    def render: dom.Node = {
      val frag = org.scalajs.dom.document.createDocumentFragment()
      val c = rx.impure.run {
        a =>
          frag.appendChild(a.render)
      }
      cancelables.register(c)
      frag
    }
  }


  implicit class NodeExtra(node: Node) {
    def setEventListener[A](key: String, listener: A => Unit): Cancelable = {
      val dyn = node.asInstanceOf[js.Dynamic]
      dyn.updateDynamic(key)(listener)
      Cancelable(() => dyn.updateDynamic(key)(null))
    }

    // Creates and inserts two empty text nodes into the DOM, which delimitate
    // a mounting region between them point. Because the DOM API only exposes
    // `.insertBefore` things are reversed: at the position of the `}`
    // character in our binding example, we insert the start point, and at `{`
    // goes the end.
    def createMountSection(): (Node, Node) = {
      val start = dom.document.createTextNode("")
      val end   = dom.document.createTextNode("")
      node.appendChild(end)
      node.appendChild(start)
      (start, end)
    }

    // Elements are then "inserted before" the start point, such that
    // inserting List(a, b) looks as follows: `}` → `a}` → `ab}`. Note that a
    // reference to the start point is sufficient here. */
    def mountHere(child: Node, start: Option[Node]): Unit =
      { start.fold(node.appendChild(child))(point => node.insertBefore(child, point)); () }

    // Cleaning stuff is equally simple, `cleanMountSection` takes a references
    // to start and end point, and (tail recursively) deletes nodes at the
    // left of the start point until it reaches end of the mounting section. */
    def cleanMountSection(start: Node, end: Node): Unit = {
      val next = start.previousSibling
      if (next != end) {
        node.removeChild(next)
        cleanMountSection(start, end)
      }
    }
  }

}
