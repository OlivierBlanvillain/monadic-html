package monixbinding

import monix.execution.Scheduler
import monix.reactive.Observable
import monix.reactive.subjects.BehaviorSubject
import org.scalajs.dom
import org.scalajs.dom.raw.{Node => DomNode}
import scala.xml.{Node => XmlNode, _}

trait Binding[+A] {
  protected[monixbinding] def observable: Observable[A]

  def map[B](f: A => B): Binding[B]              = Binding.fromObservable(observable.map(f))
  def filter(f: A => Boolean): Binding[A]        = Binding.fromObservable(observable.filter(f))
  def flatMap[B](f: A => Binding[B]): Binding[B] = Binding.fromObservable(observable.mergeMap(x => f(x).observable))
}

object Binding {
  def fromObservable[A](o: Observable[A]): Binding[A] = new Binding[A] { def observable = o }
  def apply[A](initialValue: A): Binding[A] = Var(initialValue)
}

final class Var[A](initialValue: A) extends Binding[A] {
  protected[monixbinding] val undelying = BehaviorSubject(initialValue)
  protected[monixbinding] val observable: Observable[A] = undelying

  def :=(newValue: A): Unit = undelying.onNext(newValue)
  def update(f: A => A)(implicit s: Scheduler): Unit = undelying.firstL.runAsync(v => undelying.onNext(f(v.get)))
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
        val obs = a.data.asInstanceOf[Binding[_]].observable
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
        metadata.collect {
          case m: UnprefixedAttribute if m.key == "style" =>
            elemNode.asInstanceOf[dom.html.Html].style.cssText = m.value.toString
          case m: UnprefixedAttribute =>
            elemNode.setAttribute(m.key, m.value.toString)
          case m: PrefixedAttribute if m.pre == "style" =>
            elemNode.asInstanceOf[dom.html.Html].style.setProperty(m.key, m.value.toString)
          case m: PrefixedAttribute =>
            elemNode.setAttribute(s"${m.pre}:${m.key}", m.value.toString)
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
  private implicit class DomNodeSection(node: DomNode) {
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
