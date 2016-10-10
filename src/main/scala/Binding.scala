package monixbinding

import monix.execution.Scheduler
import monix.reactive.Observable
import monix.reactive.subjects.BehaviorSubject
import org.scalajs.dom
import org.scalajs.dom.raw.{Node => DomNode}
import scala.scalajs.js
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
  def update(f: A => A)(implicit s: Scheduler): Unit = undelying.firstL.runAsync(v => undelying.onNext(f(v.get.get)))
}

object Var {
  def apply[A](initialValue: A): Var[A] = new Var(initialValue)
}

/** Side-effectly mounts an `xml.Node | Bindings[xml.Node]` tree on an actual `org.scalajs.dom.raw.Node`. */
object mount {
  def apply(parent: DomNode, child: XmlNode)(implicit s: Scheduler): Unit        = mount0(parent, child, None)
  def apply(parent: DomNode, obs: Binding[XmlNode])(implicit s: Scheduler): Unit = mount0(parent, new Atom(obs), None)

  private def mount0(parent: DomNode, child: XmlNode, mountPoint: Option[DomNode])(implicit s: Scheduler): Unit =
    child match {
      case a: Atom[_] if a.data.isInstanceOf[Binding[_]] =>
        val obs = a.data.asInstanceOf[Binding[_]].observable
        val mountPoint = dom.document.createTextNode("")
        mountPoint.mark = obs
        parent.appendChild(mountPoint)
        obs.foreach { v =>
          parent.cleanMountPoint(mountPoint, obs)
          v match {
            case n: XmlNode  => mount0(parent, n, Some(mountPoint))
            case seq: Seq[_] =>
              val nodeSeq = seq.map {
                case n: XmlNode => n
                case a => new Atom(a)
              }
              mount0(parent, new Group(nodeSeq), Some(mountPoint))
            case a => mount0(parent, new Atom(a), Some(mountPoint))
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
        parent.doMount(elemNode, mountPoint)

      case e: EntityRef  =>
        val key    = e.entityName
        val string = EntityRefMap(key).getOrElse { println(s"&${key}; is no is not a valid EntityRef."); key }
        parent.doMount(dom.document.createTextNode(string), mountPoint)

      case a: Atom[_]    => parent.doMount(dom.document.createTextNode(a.data.toString), mountPoint)
      case Comment(text) => parent.doMount(dom.document.createComment(text), mountPoint)
      case Group(nodes)  => nodes.foreach(n => mount0(parent, n, mountPoint))
      case _             => println("I'm sorry.")
    }

  private implicit class MarkableNode(node: DomNode) {
    def mark: Option[Observable[_]] =
      node.asInstanceOf[js.Dynamic].mark match {
        case o: Observable[_] => Some(o)
        case _ => None
      }

    def mark_= (obs: Observable[_]): Unit =
      node.asInstanceOf[js.Dynamic].mark = obs.asInstanceOf[js.Any]

    def cleanMountPoint(mountPoint: DomNode, obs: Observable[_]): Unit = {
      val sibling = mountPoint.previousSibling
      if (sibling != null && sibling.mark.exists(obs.==)) {
        node.removeChild(sibling)
        cleanMountPoint(mountPoint, obs)
      }
    }

    def doMount(child: DomNode, mountPoint: Option[DomNode]): Unit =
      mountPoint match {
        case None        => node.appendChild(child)
        case Some(point) => child.mark = point.mark.get; node.insertBefore(child, point)
      }
  }
}
