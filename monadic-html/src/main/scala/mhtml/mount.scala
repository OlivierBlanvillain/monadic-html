package mhtml

import org.scalajs.dom
import org.scalajs.dom.raw.{Node => DomNode}
import scala.scalajs.js
import scala.xml.{Node => XmlNode, _}

object mount {
  /** Side-effectly mounts an `xml.Node` to a `org.scalajs.dom.raw.Node`. */
  def apply(parent: DomNode, child: XmlNode): Unit =
    { mount0(parent, child, None); () }

  /** Side-effectly mounts an `Rx[xml.Node]` to a `org.scalajs.dom.raw.Node`. */
  def apply(parent: DomNode, obs: Rx[XmlNode]): Unit =
    { mount0(parent, new Atom(obs), None); () }

  private def mount0(parent: DomNode, child: XmlNode, startPoint: Option[DomNode]): Cancelable =
    child match {
      case a: Atom[_] if a.data.isInstanceOf[Rx[_]] =>
        val (start, end) = parent.createMountSection()
        var cancelable = Cancelable.empty
        a.data.asInstanceOf[Rx[_]].foreach { v =>
          parent.cleanMountSection(start, end)
          cancelable.cancel
          cancelable = v match {
            case n: XmlNode  =>
              mount0(parent, n, Some(start))
            case seq: Seq[_] =>
              val nodeSeq = seq.map {
                case n: XmlNode => n
                case a => new Atom(a)
              }
              mount0(parent, new Group(nodeSeq), Some(start))
            case a =>
              mount0(parent, new Atom(a), Some(start))
          }
        }

      case e @ Elem(_, label, metadata, _, child @ _*) =>
        val elemNode = dom.document.createElement(label)

        val cancelMetadata: Cancelable = metadata.value match {
          case null => Cancelable.empty
          case (a: Atom[_]) :: _ if a.data.isInstanceOf[Function0[_]] =>
            elemNode.setEventListener(metadata, (_: dom.Event) => a.data.asInstanceOf[Function0[Unit]]())
          case (a: Atom[_]) :: _ if a.data.isInstanceOf[Function1[_, _]] =>
            elemNode.setEventListener(metadata, a.data.asInstanceOf[Function1[dom.Event, Unit]])
          case (a: Atom[_]) :: _ if a.data.isInstanceOf[Rx[_]] =>
            a.data.asInstanceOf[Rx[_]]
              .foreach(value => elemNode.setMetadata(metadata, Some(value.toString)))
          case _ =>
            elemNode.setMetadata(metadata, None)
            Cancelable.empty
        }
        val cancels = child.map(c => mount0(elemNode, c, None))
        parent.mountHere(elemNode, startPoint)
        Cancelable { () => cancelMetadata.cancel(); cancels.foreach(_.cancel()) }

      case e: EntityRef  =>
        val key    = e.entityName
        val string = EntityRefMap(key)
        parent.mountHere(dom.document.createTextNode(string), startPoint)
        Cancelable.empty

      case a: Atom[_]    =>
        val content = a.data.toString
        if (!content.isEmpty)
          parent.mountHere(dom.document.createTextNode(content), startPoint)
        Cancelable.empty

      case Comment(text) =>
        parent.mountHere(dom.document.createComment(text), startPoint)
        Cancelable.empty

      case Group(nodes)  =>
        val cancels = nodes.map(n => mount0(parent, n, startPoint))
        Cancelable(() => cancels.foreach(_.cancel))
    }

  private implicit class DomNodeExtra(node: DomNode) {
    def setEventListener(metadata: MetaData, listener: dom.Event => Unit): Cancelable =
      metadata.headOption.map {
        case m: UnprefixedAttribute =>
          val dyn = node.asInstanceOf[js.Dynamic]
          dyn.updateDynamic(m.key)(listener)
          Cancelable(() => dyn.updateDynamic(m.key)(null))
      }.getOrElse(Cancelable.empty)

    def setMetadata(metadata: MetaData, value: Option[String]): Unit = {
      val htmlNode = node.asInstanceOf[dom.html.Html]
      metadata.foreach {
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

    // Creats and inserts two empty text nodes into the DOM, which delimitate
    // a mounting region between them point. Because the DOM API only exposes
    // `.insertBefore` things are reversed: at the position of the `}`
    // character in our binding example, we insert the start point, and at `{`
    // goes the end.
    def createMountSection(): (DomNode, DomNode) = {
      val start = dom.document.createTextNode("")
      val end   = dom.document.createTextNode("")
      node.appendChild(end)
      node.appendChild(start)
      (start, end)
    }

    // Elements are then "inserted before" the start point, such that
    // inserting List(a, b) looks as follows: `}` → `a}` → `ab}`. Note that a
    // reference to the start point is sufficient here. */
    def mountHere(child: DomNode, start: Option[DomNode]): Unit =
      { start.fold(node.appendChild(child))(point => node.insertBefore(child, point)); () }

    // Cleaning stuff is equally simple, `cleanMountSection` takes a references
    // to start and end point, and (tail recursively) deletes nodes at the
    // left of the start point until it reaches end of the mounting section. */
    def cleanMountSection(start: DomNode, end: DomNode): Unit = {
      val next = start.previousSibling
      if (next != end) {
        node.removeChild(next)
        cleanMountSection(start, end)
      }
    }
  }
}
