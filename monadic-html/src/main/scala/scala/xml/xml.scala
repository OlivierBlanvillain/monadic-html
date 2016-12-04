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

final case class UnprefixedAttribute[T] private (
  key: String,
  value: Node,
  next: MetaData
) extends MetaData {
  def this(key: String, e: T, next: MetaData)(implicit ev: XmlSerializable[T]) =
    this(
      key,
      e match {
        case s: String => Text(s) // convenient for: attr={"computed string"}
        case n: Node => n
        // Iterable[Node] does not make sense in UnprefixedAttribute.
        case _ => new Atom(e)
      },
      next
    )
}

@scala.annotation.implicitNotFound(msg =
  """Cannot embed value of type '${T}' into an xml literal. Allowed types are:
Base types:
  - String (tip: you can use ${T}.toString)
  - Int
  - xml.Node
  - List[xml.Node], Seq[xml.Node] and other Iterable containers.
Event handlers:
  - () => Unit
  - T => Unit, where T can be any type.
Monadic-html:
  - mhtml.Rx[T] or mhtml.Var[T], where T is a type that can be embedded into an xml literal. For example, a String or Seq[xml.Node].
""")
sealed trait XmlSerializable[T]
object XmlSerializable{
  import language.higherKinds
  // base types
  implicit val intSerializable: XmlSerializable[Int] = new XmlSerializable[Int] {}
  implicit val stringSerializable: XmlSerializable[String] = new XmlSerializable[String] {}
  implicit def nodeSerializable[T <: Node]: XmlSerializable[T] = new XmlSerializable[T] {}
  implicit def iterableSerializable[C[_], T <: Node](implicit conv: C[T] => Iterable[T]) = new XmlSerializable[C[T]] {}
  // event handlers
  implicit val function0Serializable: XmlSerializable[() => Unit] = new XmlSerializable[() => Unit] {}
  implicit def function1Serializable[T]: XmlSerializable[(T) => Unit] = new XmlSerializable[T => Unit] {}
  // mhtml
  implicit def rxSerializable[C[_] <: mhtml.Rx[_], T: XmlSerializable]: XmlSerializable[C[T]] = new XmlSerializable[C[T]] {}
}

// Internal structure used by scalac to create literals -----------------------
class NodeBuffer extends scala.collection.mutable.ArrayBuffer[Node] {
  def &+(e: Iterable[Node]): NodeBuffer = { e foreach &+; this }
  def &+(e: Node): NodeBuffer = { super.+=(e); this }
  def &+[A:XmlSerializable](e: A): NodeBuffer = { super.+=(new Atom(e)); this }
}
