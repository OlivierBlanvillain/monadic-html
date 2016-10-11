package mhtml

import monix.execution.Scheduler
import monix.reactive.Observable
import monix.reactive.subjects.BehaviorSubject
import org.scalajs.dom
import org.scalajs.dom.raw.{Node => DomNode}
import scala.scalajs.js
import scala.xml.{Node => XmlNode, _}

trait Binding[+A] {
  def underlying: Observable[A]

  def map[B](f: A => B): Binding[B]              = Binding.fromObservable(underlying.map(f))
  def filter(f: A => Boolean): Binding[A]        = Binding.fromObservable(underlying.filter(f))
  def flatMap[B](f: A => Binding[B]): Binding[B] = Binding.fromObservable(underlying.mergeMap(x => f(x).underlying))
  def foreach(f: A => Unit)(implicit s: Scheduler): Unit = underlying.foreach(f)
}

object Binding {
  def fromObservable[A](o: Observable[A]): Binding[A] = new Binding[A] { def underlying = o }
  def apply[A](initialValue: A): Binding[A] = Var(initialValue)
}

final class Var[A](initialValue: A) extends Binding[A] {
  private val subject = BehaviorSubject(initialValue)
  val underlying: Observable[A] = subject

  def :=(newValue: A): Unit = subject.onNext(newValue)
  def update(f: A => A)(implicit s: Scheduler): Unit = subject.firstL.runAsync(v => subject.onNext(f(v.get)))
}

object Var {
  def apply[A](initialValue: A): Var[A] = new Var(initialValue)
}

/** Side-effectly mounts an `xml.Node | Bindings[xml.Node]` on a concrete `org.scalajs.dom.raw.Node`. */
object mount {
  def apply(parent: DomNode, child: XmlNode)(implicit s: Scheduler): Unit        = mount0(parent, child, None)
  def apply(parent: DomNode, obs: Binding[XmlNode])(implicit s: Scheduler): Unit = mount0(parent, new Atom(obs), None)

  private def mount0(parent: DomNode, child: XmlNode, startPoint: Option[DomNode])(implicit s: Scheduler): Unit =
    child match {
      case a: Atom[_] if a.data.isInstanceOf[Binding[_]] =>
        val obs = a.data.asInstanceOf[Binding[_]].underlying
        val (start, end) = parent.createMountSection()
        obs.foreach { v =>
          parent.cleanMountSection(start, end)
          v match {
            case n: XmlNode  => mount0(parent, n, Some(start))
            case seq: Seq[_] =>
              val nodeSeq = seq.map {
                case n: XmlNode => n
                case a => new Atom(a)
              }
              mount0(parent, new Group(nodeSeq), Some(start))
            case a => mount0(parent, new Atom(a), Some(start))
          }
        }

      case e @ Elem(_, label, metadata, _, child @ _*) =>
        val elemNode = dom.document.createElement(label)

        metadata.value match {
          case null => ()
          case (a: Atom[_]) :: _ if a.data.isInstanceOf[Function0[_]] =>
            elemNode.setEventListener(metadata, (_: dom.Event) => a.data.asInstanceOf[Function0[Unit]]())
          case (a: Atom[_]) :: _ if a.data.isInstanceOf[Function1[_, _]] =>
            elemNode.setEventListener(metadata, a.data.asInstanceOf[Function1[dom.Event, Unit]])
          case (a: Atom[_]) :: _ if a.data.isInstanceOf[Binding[_]] =>
            a.data.asInstanceOf[Binding[_]].underlying
              .foreach(value => elemNode.setMetadata(metadata, Some(value.toString)))
          case _ =>
            elemNode.setMetadata(metadata, None)
        }
        child.foreach(c => mount0(elemNode, c, None))
        parent.mountInSection(elemNode, startPoint)

      case e: EntityRef  =>
        val key    = e.entityName
        val string = EntityRefMap(key)
        parent.mountInSection(dom.document.createTextNode(string), startPoint)

      case a: Atom[_]    =>
        val content = a.data.toString
        if (!content.isEmpty)
          parent.mountInSection(dom.document.createTextNode(content), startPoint)

      case Comment(text) => parent.mountInSection(dom.document.createComment(text), startPoint)
      case Group(nodes)  => nodes.foreach(n => mount0(parent, n, startPoint))
    }

  /** For this ScalaDoc, suppose the following binding: `<div><br>{}<hr></div>`. */
  private implicit class DomNodeExtra(node: DomNode) {
    def setEventListener(metadata: MetaData, listener: dom.Event => Unit): Unit =
      metadata.collect {
        case m: UnprefixedAttribute =>
          node.asInstanceOf[js.Dynamic].updateDynamic(m.key)(listener)
      }

    def setMetadata(metadata: MetaData, value: Option[String]): Unit = {
      val htmlNode = node.asInstanceOf[dom.html.Html]
      metadata.collect {
        case m: UnprefixedAttribute if m.key == "style" =>
          htmlNode.style.cssText = value.getOrElse(m.value.toString)
        case m: UnprefixedAttribute =>
          htmlNode.setAttribute(m.key, value.getOrElse(m.value.toString))
        case m: PrefixedAttribute if m.pre == "style" =>
          htmlNode.style.setProperty(m.key, value.getOrElse(m.value.toString))
        case m: PrefixedAttribute =>
          htmlNode.setAttribute(s"${m.pre}:${m.key}", value.getOrElse(m.value.toString))
      }
    }

    /**
     * Creats and inserts two empty text nodes into the DOM, which delimitate
     * a mounting region between them point. Because the DOM API only exposes
     * `.insertBefore` things are reversed: at the position of the `}`
     * character in our binding example, we insert the start point, and at `{`
     * goes the end.
     */
    def createMountSection(): (DomNode, DomNode) = {
      val start = dom.document.createTextNode("")
      val end   = dom.document.createTextNode("")
      node.appendChild(end)
      node.appendChild(start)
      (start, end)
    }

    /**
     * Elements are then "inserted before" the start point, such that
     * inserting List(a, b) looks as follows: `}` → `a}` → `ab}`. Note that a
     * reference to the start point is sufficient here.
     */
    def mountInSection(child: DomNode, start: Option[DomNode]): Unit =
      start.fold(node.appendChild(child))(point => node.insertBefore(child, point))

    /**
     * Cleaning stuff is equally simple, `cleanMountSection` takes a references
     * to start and end point, and (tail recursively) deletes nodes at the
     * left of the start point until it reaches end of the mounting section.
     */
    def cleanMountSection(start: DomNode, end: DomNode): Unit = {
      val next = start.previousSibling
      if (next != end) {
        node.removeChild(next)
        cleanMountSection(start, end)
      }
    }
  }
}
