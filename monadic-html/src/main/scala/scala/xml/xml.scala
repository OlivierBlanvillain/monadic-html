package scala.xml

// XML Nodes ------------------------------------------------------------------

// Nodes are now implemented as an idiomatic sealed hierarchy of case classes.
// `Atom` was kept as a class for source compatibility (even if it's not used
// directly in scalac paser). `NoteSeq` direpeared, `Node <:!< Seq[Node]`...

/** Trait representing XML tree. */
sealed trait Node

/** A hack to group XML nodes in one node. */
final case class Group(nodes: Seq[Node]) extends Node

/** XML element. */
final case class Elem(
  prefix: String,
  label: String,
  attributes1: MetaData,
  scope: TopScope.type,
  child: Node*
) extends Node {
  def this(p: String, l: String, a: MetaData, s: TopScope.type, m: Boolean, c: Node*) =
    this(p, l, a, s, c: _*) // m, former minimizeEmpty, is now thrown away.
}

case object TopScope

/** XML leaf for comments. */
final case class Comment(commentText: String) extends Node

/** XML leaf for entity references. */
final case class EntityRef(entityName: String) extends Node

/** XML leaf for text. */
final case class Text(text: String) extends Atom[String](text)

/** XML leaf container for any data of type `A`. */
class Atom[+A](val data: A) extends Node

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

final case class PrefixedAttribute(
  pre: String,
  key: String,
  value: Node,
  next: MetaData
) extends MetaData {
  def this(pre: String, key: String, value: String, next: MetaData) =
    this(pre, key, Text(value), next)
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
- String, Boolean
- xml.Text
- Option[String]
- () => Unit, T => Unit event handler. Note: The return type needs to be Unit!
- mhtml.Var[T], mhtml.Rx[T] where T can be embedded in xml attribute position.
""")
trait XmlAttributeEmbeddable[T] { def toNode(e: T): Node }
object XmlAttributeEmbeddable{
  import language.higherKinds

  @inline def instance[T](f: T => Node): XmlAttributeEmbeddable[T] =
    new XmlAttributeEmbeddable[T] { override def toNode(e: T): Node = f(e) }
  @inline def atom[T]: XmlAttributeEmbeddable[T] = instance[T](new Atom(_))

  implicit val booleanAttributeEmbeddable: XmlAttributeEmbeddable[Boolean]             = atom[Boolean]
  implicit val optionStringAttributeEmbeddable: XmlAttributeEmbeddable[Option[String]] = atom[Option[String]]
  implicit val stringAttributeEmbeddable: XmlAttributeEmbeddable[String]               = instance[String](Text.apply)
  implicit val textNodeAttributeEmbeddable: XmlAttributeEmbeddable[Text]               = instance(identity)
  implicit val function0AttributeEmbeddable: XmlAttributeEmbeddable[() => Unit]        = atom[() => Unit]
  implicit def function1AttributeEmbeddable[T]: XmlAttributeEmbeddable[T => Unit]      = atom[T => Unit]
  implicit def rxAttributeEmbeddable[C[_] <: mhtml.Rx[_], T: XmlAttributeEmbeddable]: XmlAttributeEmbeddable[C[T]] = atom[C[T]]
}

/** Evidence that T can be embedded in xml element position. */
@scala.annotation.implicitNotFound(msg =
    """Cannot embed value of type ${T} in xml element, implicit XmlElementEmbeddable[${T}] not found.
The following types are supported:
- String, Int, Long, Double, Float, Char, Boolean
- xml.Node, Seq[xml.Node]
- mhtml.Var[T], mhtml.Rx[T] where T can be embedded in xml element position.
- Option[T] where T can be embedded in xml element position.
""")
trait XmlElementEmbeddable[T] { def toNode(e: T): Node }
object XmlElementEmbeddable {
  import language.higherKinds

  @inline def instance[T](f: T => Node): XmlElementEmbeddable[T] =
    new XmlElementEmbeddable[T] { override def toNode(e: T): Node = f(e) }
  @inline def atom[T]: XmlElementEmbeddable[T] = instance[T](new Atom(_))

  implicit val intElementEmbeddable: XmlElementEmbeddable[Int]           = atom[Int]
  implicit val floatElementEmbeddable: XmlElementEmbeddable[Float]       = atom[Float]
  implicit val doubleElementEmbeddable: XmlElementEmbeddable[Double]     = atom[Double]
  implicit val longElementEmbeddable: XmlElementEmbeddable[Long]         = atom[Long]
  implicit val charElementEmbeddable: XmlElementEmbeddable[Char]         = instance[Char](x => Text(x.toString))
  implicit val stringElementEmbeddable: XmlElementEmbeddable[String]     = instance[String](Text.apply)
  implicit def nodeElementEmbeddable[T <: Node]: XmlElementEmbeddable[T] = instance[T](identity)
  implicit def optionElementEmbeddable[T: XmlElementEmbeddable]: XmlElementEmbeddable[Option[T]] = atom[Option[T]]
  implicit def seqElementEmbeddable[C[_] <: Seq[_], T <: Node]: XmlElementEmbeddable[C[T]] = instance[C[T]](
    // For some odd reason, we get a type error without the cast.
    lst => Group(lst.asInstanceOf[Seq[Node]])
  )
  implicit def rxElementEmbeddable[C[_] <: mhtml.Rx[_], T: XmlElementEmbeddable]: XmlElementEmbeddable[C[T]] = atom[C[T]]
}

// Internal structure used by scalac to create literals -----------------------
class NodeBuffer extends scala.collection.mutable.ArrayBuffer[Node] {
  def &+[A](e: A)(implicit ev: XmlElementEmbeddable[A]): NodeBuffer = { super.+=(ev.toNode(e)); this }
}
