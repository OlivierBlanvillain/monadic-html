package scala.xml

import scala.collection.mutable
import scala.annotation.implicitNotFound

// XML Nodes ------------------------------------------------------------------

// Nodes are now implemented as an idiomatic sealed hierarchy of case classes.
// `Atom` was kept as a class for source compatibility (even if it's not used
// directly in scalac paser). `NoteSeq` disappeared, `Node <:!< Seq[Node]`...

/** Trait representing XML tree. */
sealed trait Node {
  def scope: Option[Scope] = None
  def prefix: Option[String] = None
  def namespace: Option[String] = scope.flatMap(_.namespaceURI(prefix.orNull))
}

/** A hack to group XML nodes in one node. */
final case class Group(nodes: Seq[Node]) extends Node

/** XML element. */
final case class Elem(
  override val prefix: Option[String],
  label: String,
  attributes1: MetaData,
  override val scope: Option[Scope],
  minimizeEmpty: Boolean,
  child: Node*
) extends Node {
  def this(p: String, l: String, a: MetaData, s: Scope, m: Boolean, c: Node*) =
    this(Option(p), l, Elem.merge(a, s), Some(s), m, c: _*)
}
object Elem {
  // Merges attributes stored in as scope with other metadata
  private def merge(acc: MetaData, s: Scope): MetaData = s match {
    case NamespaceBinding(null, url, next) =>
      merge(UnprefixedAttribute("xmlns", url, acc), next)
    case NamespaceBinding(key, url, next) =>
      merge(PrefixedAttribute("xmlns", key, Text(url), acc), next)
    case _ => acc
  }
}

/** XML leaf for comments. */
final case class Comment(commentText: String) extends Node

/** XML leaf for entity references. */
final case class EntityRef(entityName: String)(implicit ev: XmlEntityRefEmbeddable) extends Node

/** XML leaf for text. */
final case class Text(text: String) extends Atom[String](text)

/** XML leaf container for any data of type `A`. */
class Atom[+A](val data: A) extends Node

// Scopes ---------------------------------------------------------------------

sealed trait Scope {
  def namespaceURI(prefix: String) : Option[String]
}

// Used by scalac for xmlns prefixed attributes
class NamespaceBinding(key: String, url: String, next: NamespaceBinding) extends Scope {
  def isEmpty = false
  def get     = this
  def _1      = key
  def _2      = url
  def _3      = next

  def namespaceURI(prefix: String) : Option[String] =
    if (prefix == key) Some(url) else next namespaceURI prefix
}

object NamespaceBinding {
  def unapply(s: NamespaceBinding): NamespaceBinding = s
}

final case object TopScope extends NamespaceBinding(null, null, null) {
  override def namespaceURI(prefix: String): Option[String] = None
}

// XML Metadata ---------------------------------------------------------------

// `Attibutes`, the trait which used to be in between Metadata and {Prefixed,
// Unprefixed}Attribute was removed. Iterable[MetaData] <: Metadata still holds,
// but this should never be user facing.

/**
 * This class represents an attribute and at the same time a linked list of
 *  attributes. Every instance of this class is either
 *  - an instance of `UnprefixedAttribute key,value` or
 *  - an instance of `PrefixedAttribute namespace_prefix,key,value` or
 *  - `Null`, the empty attribute list.
 */
sealed trait MetaData {
  def hasNext = (Null != next)
  def key: String
  def value: Node
  def next: MetaData
  def map[T](f: MetaData => T): List[T] = {
    def map0(acc: List[T], m: MetaData): List[T] =
      m match {
        case Null => acc
        case any  => map0(f(any) :: acc, m.next)
      }
    map0(Nil, this)
  }
}

case object Null extends MetaData {
  def next = null
  def key = null
  def value = null
}

final case class PrefixedAttribute[T: XmlAttributeEmbeddable](
  pre: String,
  key: String,
  e: T,
  next: MetaData
)(implicit ev: XmlAttributeEmbeddable[T]) extends MetaData {
  val value: Node = e match { case n: Node => n; case _ => new Atom(e) }
}

final case class UnprefixedAttribute[T](
  key: String,
  e: T,
  next: MetaData
)(implicit ev: XmlAttributeEmbeddable[T]) extends MetaData {
  val value: Node = e match { case n: Node => n; case _ => new Atom(e) }
}

/** Evidence that T can be embedded in xml attribute position. */
@implicitNotFound(msg =
    """Cannot embed value of type ${T} in xml attribute, implicit XmlAttributeEmbeddable[${T}] not found.
The following types are supported:
- String
- Boolean (false → remove attribute, true → empty attribute)
- () => Unit, T => Unit event handler. Note: The return type needs to be Unit!
- mhtml.Var[T], mhtml.Rx[T] where T is XmlAttributeEmbeddable
- Option[T] where T is XmlAttributeEmbeddable (None → remove from the DOM)
""")
trait XmlAttributeEmbeddable[T]
object XmlAttributeEmbeddable {
  type XA[T] = XmlAttributeEmbeddable[T]
  @inline implicit def noneAttributeEmbeddable:                             XA[None.type]    = null
  @inline implicit def booleanAttributeEmbeddable:                          XA[Boolean]      = null
  @inline implicit def stringAttributeEmbeddable:                           XA[String]       = null
  @inline implicit def textNodeAttributeEmbeddable:                         XA[Text]         = null
  @inline implicit def function0AttributeEmbeddable:                        XA[() => Unit]   = null
  @inline implicit def function1AttributeEmbeddable[T]:                     XA[T => Unit]    = null
  @inline implicit def optionAttributeEmbeddable[C[x] <: Option[x], T: XA]: XA[C[T]]         = null
  @inline implicit def rxAttributeEmbeddable[C[x] <: mhtml.Rx[x], T: XA]:   XA[C[T]]         = null
}

/** Evidence that T can be embedded in xml element position. */
@implicitNotFound(msg =
    """Cannot embed value of type ${T} in xml element, implicit XmlElementEmbeddable[${T}] not found.
The following types are supported:
- String, Int, Long, Double, Float, Char (converted with .toString)
- xml.Node, Seq[xml.Node]
- mhtml.Var[T], mhtml.Rx[T] where T is XmlElementEmbeddable
- Option[T] where T is XmlElementEmbeddable (None → remove from the DOM)
""")
trait XmlElementEmbeddable[T]
object XmlElementEmbeddable {
  type XE[T] = XmlElementEmbeddable[T]

  @inline implicit def nilElementEmbeddable:                              XE[Nil.type]  = null
  @inline implicit def noneElementEmbeddable:                             XE[None.type] = null
  @inline implicit def intElementEmbeddable:                              XE[Int]       = null
  @inline implicit def floatElementEmbeddable:                            XE[Float]     = null
  @inline implicit def doubleElementEmbeddable:                           XE[Double]    = null
  @inline implicit def longElementEmbeddable:                             XE[Long]      = null
  @inline implicit def charElementEmbeddable:                             XE[Char]      = null
  @inline implicit def stringElementEmbeddable:                           XE[String]    = null
  @inline implicit def nodeElementEmbeddable[T <: Node]:                  XE[T]         = null
  @inline implicit def optionElementEmbeddable[C[x] <: Option[x], T: XE]: XE[C[T]]      = null
  @inline implicit def seqElementEmbeddable[C[x] <: Seq[x], T <: Node]:   XE[C[T]]      = null
  @inline implicit def rxElementEmbeddable[C[x] <: mhtml.Rx[x], T: XE]:   XE[C[T]]      = null
}

@implicitNotFound("""EntityRef are not supported, use Strings instead: <p>{"<"}</p>""")
trait XmlEntityRefEmbeddable

/** Internal structure used by scalac to create literals */
class NodeBuffer extends Seq[Node] {
  private val underlying: mutable.ArrayBuffer[Node] = mutable.ArrayBuffer.empty
  def iterator: Iterator[Node] = underlying.iterator
  def apply(i: Int): Node = underlying.apply(i)
  def length: Int = underlying.length
  override def toString: String = underlying.toString

  def &+(e: Node): NodeBuffer = { underlying.+=(e); this }
  def &+[A: XmlElementEmbeddable](e: A): NodeBuffer = { underlying.+=(new Atom(e)); this }
}
