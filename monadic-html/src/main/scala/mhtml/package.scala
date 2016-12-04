import scala.xml.Group
import scala.xml.Node
import scala.xml.Text
import scala.xml.XmlAttributeEmbeddable
import scala.xml.XmlElementEmbeddable

package object mhtml {
  import language.higherKinds
  import XmlAttributeEmbeddable.{atom => attrAtom, instance => attrInstance}
  import XmlElementEmbeddable.{atom, instance}
  // element embeddables
  implicit val intElementEmbeddable: XmlElementEmbeddable[Int]             = atom[Int]
  implicit val floatElementEmbeddable: XmlElementEmbeddable[Float]         = atom[Float]
  implicit val doubleElementEmbeddable: XmlElementEmbeddable[Double]       = atom[Double]
  implicit val longElementEmbeddable: XmlElementEmbeddable[Long]           = atom[Long]
  implicit val stringElementEmbeddable: XmlElementEmbeddable[String]       = instance[String](Text.apply)
  implicit def nodeElementEmbeddable[T <: Node]: XmlElementEmbeddable[T]   = instance[T](identity)
  implicit def iterableElementEmbeddable[C[_], T](implicit
      ev: XmlElementEmbeddable[T],
      conv: C[T] => Iterable[T]
  ): XmlElementEmbeddable[C[T]] =
    instance[C[T]](x => Group(conv(x).map(ev.toNode).toSeq))
  implicit def seqElementEmbeddable[T <: Node]: XmlElementEmbeddable[Seq[T]] = instance[Seq[T]](x => Group(x))
  implicit def rxElementEmbeddable[C[_] <: mhtml.Rx[_], T: XmlElementEmbeddable]: XmlElementEmbeddable[C[T]] = atom[C[T]]

  // attribute embeddables
  implicit val function0AttributeEmbeddable: XmlAttributeEmbeddable[() => Unit]   = attrAtom[() => Unit]
  implicit def function1AttributeEmbeddable[T]: XmlAttributeEmbeddable[T => Unit] = attrAtom[T => Unit]
  // allow Rx[A => Unit] in attribute positions.
  implicit def rxAttributeEmbeddable[C[_] <: mhtml.Rx[_], T: XmlAttributeEmbeddable]: XmlAttributeEmbeddable[C[T]]        = attrAtom[C[T]]
  implicit def elementEmbeddableIsAttributeEmbeddable[T](implicit ev: XmlElementEmbeddable[T]): XmlAttributeEmbeddable[T] = attrInstance(ev.toNode)

  /** Replacement for innerHTML in the DOM APT. In general, setting HTML from
    * String is risky because it's easy to inadvertently expose your users to a
    * cross-site scripting (XSS) attack. Using [[unsafeRawHTML]] in conjunction
    * with other `Atom`s in a single `Node` has undefined behavior. */
  def unsafeRawHTML(rawHtml: String): Node = new xml.Atom(new UnsafeRawHTML(rawHtml))
}
