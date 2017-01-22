package scala.xml

import language.higherKinds

// XML Nodes ------------------------------------------------------------------

// Nodes are now implemented as an idiomatic sealed hierarchy of case classes.
// `Atom` was kept as a class for source compatibility (even if it's not used
// directly in scalac paser). `NoteSeq` disappeared, `Node <:!< Seq[Node]`...

/** Trait representing XML tree. */
sealed trait Node

/** A hack to group XML nodes in one node. */
final case class Group(nodes: Seq[Node]) extends Node

/** XML element. */
final case class Elem(
  label: String,
  attributes1: MetaData,
  child: Node*
) extends Node {
  // m, former minimizeEmpty, and p former prefix are now thrown away.
  def this(p: String, l: String, a: MetaData, s: Scope, m: Boolean, c: Node*) =
    this(l, {
      // Merges attributes stored in as scope with other metadata
      def merge(acc: MetaData, s: Scope): MetaData = s match {
        case NamespaceBinding(null, url, next) =>
          merge(UnprefixedAttribute("xmlns", url, acc), next)
        case NamespaceBinding(key, url, next) =>
          merge(PrefixedAttribute("xmlns", key, Text(url), acc), next)
        case _ => acc
      }
      merge(a, s)
    }, c: _*)
}

/** XML leaf for comments. */
final case class Comment(commentText: String) extends Node

/** XML leaf for entity references. */
final case class EntityRef(entityName: String) extends Node

/** XML leaf for text. */
final case class Text(text: String) extends Atom[String](text)

/** XML leaf container for any data of type `A`. */
class Atom[+A](val data: A) extends Node

// Scopes ---------------------------------------------------------------------

sealed trait Scope

// Used by scalac for xmlns prefixed attributes
class NamespaceBinding(key: String, url: String, next: Scope) extends Scope {
  def isEmpty = false
  def get     = this
  def _1      = key
  def _2      = url
  def _3      = next
}

object NamespaceBinding {
  def unapply(s: NamespaceBinding): NamespaceBinding = s
}

final case object TopScope extends NamespaceBinding(null, null, null)

// XML Metadata ---------------------------------------------------------------

// `Attibutes`, the trait which used to be in between Metadata and {Prefixed,
// Unprefixed}Attribute was removed. Iterable[MetaData] <: Metadata still holds,
// but this should never be user facing.

/** This class represents an attribute and at the same time a linked list of
  *  attributes. Every instance of this class is either
  *  - an instance of `UnprefixedAttribute key,value` or
  *  - an instance of `PrefixedAttribute namespace_prefix,key,value` or
  *  - `Null, the empty attribute list. */
sealed trait MetaData extends Iterable[MetaData] {
  def hasNext = (Null != next)
  def key: String
  def value: Node
  def next: MetaData

  def iterator: Iterator[MetaData] =
    Iterator.single(this) ++ next.iterator
}

case object Null extends MetaData {
  def next = null
  def key = null
  def value = null
  override def iterator = Iterator.empty
}

final case class PrefixedAttribute[T](
  pre: String,
  key: String,
  e: T,
  next: MetaData
)(implicit ev: XmlAttributeEmbeddable[T]) extends MetaData {
  override val value: Node = ev.toNode(e)
}

final case class UnprefixedAttribute[T](
  key: String,
  e: T,
  next: MetaData
)(implicit ev: XmlAttributeEmbeddable[T]) extends MetaData {
  override val value: Node = ev.toNode(e)
}

/** Evidence that T can be embedded in xml attribute position. */
@scala.annotation.implicitNotFound(msg =
    """Cannot embed value of type ${T} in xml attribute, implicit XmlAttributeEmbeddable[${T}] not found.
The following types are supported:
- String
- Boolean (false → remove attribute, true → empty attribute)
- () => Unit, T => Unit event handler. Note: The return type needs to be Unit!
- mhtml.Var[T], mhtml.Rx[T] where T is XmlAttributeEmbeddable
- Option[T] where T is XmlAttributeEmbeddable (None → remove from the DOM)
""")
trait XmlAttributeEmbeddable[T] { def toNode(e: T): Node }
object XmlAttributeEmbeddable {
  @inline def instance[T](f: T => Node): XmlAttributeEmbeddable[T] =
    new XmlAttributeEmbeddable[T] { override def toNode(e: T): Node = f(e) }
  @inline def atom[T]: XmlAttributeEmbeddable[T] = instance[T](new Atom(_))

  implicit val noneAttributeEmbeddable: XmlAttributeEmbeddable[None.type]         = atom[None.type]
  implicit val nilAttributeEmbeddable: XmlAttributeEmbeddable[Nil.type]           = atom[Nil.type]
  implicit val booleanAttributeEmbeddable: XmlAttributeEmbeddable[Boolean]        = atom[Boolean]
  implicit val stringAttributeEmbeddable: XmlAttributeEmbeddable[String]          = instance[String](Text.apply)
  implicit val textNodeAttributeEmbeddable: XmlAttributeEmbeddable[Text]          = instance(identity)
  implicit val function0AttributeEmbeddable: XmlAttributeEmbeddable[() => Unit]   = atom[() => Unit]
  implicit def function1AttributeEmbeddable[T]: XmlAttributeEmbeddable[T => Unit] = atom[T => Unit]
  implicit def optionAttributeEmbeddable[C[x] <: Option[x], T: XmlAttributeEmbeddable]: XmlAttributeEmbeddable[C[T]] = atom[C[T]]
  implicit def rxAttributeEmbeddable[C[x] <: mhtml.Rx[x], T: XmlAttributeEmbeddable]: XmlAttributeEmbeddable[C[T]]   = atom[C[T]]
}

/** Evidence that T can be embedded in xml element position. */
@scala.annotation.implicitNotFound(msg =
    """Cannot embed value of type ${T} in xml element, implicit XmlElementEmbeddable[${T}] not found.
The following types are supported:
- String, Int, Long, Double, Float, Char (converted with .toString)
- xml.Node, Seq[xml.Node]
- mhtml.Var[T], mhtml.Rx[T] where T is XmlElementEmbeddable
- Option[T] where T is XmlElementEmbeddable (None → remove from the DOM)
""")
trait XmlElementEmbeddable[T] { def toNode(e: T): Node }
object XmlElementEmbeddable {
  @inline def instance[T](f: T => Node): XmlElementEmbeddable[T] =
    new XmlElementEmbeddable[T] { override def toNode(e: T): Node = f(e) }
  @inline def atom[T]: XmlElementEmbeddable[T] = instance[T](new Atom(_))

  implicit val nilElementEmbeddable: XmlElementEmbeddable[Nil.type]      = atom[Nil.type]
  implicit val noneElementEmbeddable: XmlElementEmbeddable[None.type]    = atom[None.type]
  implicit val intElementEmbeddable: XmlElementEmbeddable[Int]           = atom[Int]
  implicit val floatElementEmbeddable: XmlElementEmbeddable[Float]       = atom[Float]
  implicit val doubleElementEmbeddable: XmlElementEmbeddable[Double]     = atom[Double]
  implicit val longElementEmbeddable: XmlElementEmbeddable[Long]         = atom[Long]
  implicit val charElementEmbeddable: XmlElementEmbeddable[Char]         = instance[Char](x => Text(x.toString))
  implicit val stringElementEmbeddable: XmlElementEmbeddable[String]     = instance[String](Text.apply)
  implicit def nodeElementEmbeddable[T <: Node]: XmlElementEmbeddable[T] = instance[T](identity)
  implicit def optionElementEmbeddable[C[x] <: Option[x], T: XmlElementEmbeddable]: XmlElementEmbeddable[C[T]] = atom[C[T]]

  // Here we really want `C[x] <: Seq[x]` instead of `C[_] <: Seq[_]`. `x` is
  // deliberately lower case to indicate it's not a normal type parameter: it
  // has a very local scope. To understand why these two definitions are not
  // equivalent, one needs to understand that in `C[_] <: Seq[_]`, both `_`
  // have a different meaning. On the left hand side, `C[_]` means that `C` is
  // a type constructor taking a single type argument of kind `*`. On the
  // right hand side, the `_` in `Seq[_]` indicated a *wildcard* type: it's
  // essentially a shorthand for `Seq[t] forSome { type t >: Nothing <: Any]
  // }`, which is itself of kind `*`. To summarize, `C[_] <: Seq[_]` means a
  // higher kinded type of kind `(* -> *)` which is a subtype of `Seq[Any]`
  // (makes no sense), While `C[x] <: Seq[x]` means a higher kinded type of
  // kind `(* -> *)` which, when instantiated with a type `x`, is a subtype of
  // `Seq[x]` (what we actually want).

  implicit def seqElementEmbeddable[C[x] <: Seq[x], T <: Node]: XmlElementEmbeddable[C[T]] = instance[C[T]](Group.apply)
  implicit def rxElementEmbeddable[C[x] <: mhtml.Rx[x], T: XmlElementEmbeddable]: XmlElementEmbeddable[C[T]] = atom[C[T]]
}

/** Internal structure used by scalac to create literals */
class NodeBuffer extends scala.collection.mutable.ArrayBuffer[Node] {
  def &+[A](e: A)(implicit ev: XmlElementEmbeddable[A]): NodeBuffer = { super.+=(ev.toNode(e)); this }
}
