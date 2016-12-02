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

final case class UnprefixedAttribute(
  key: String,
  value: Node,
  next: MetaData
) extends MetaData {
  def this(key: String, value: String, next: MetaData) =
    this(key, Text(value), next)
}

// Internal structure used by scalac to create literals -----------------------

class NodeBuffer extends scala.collection.mutable.ArrayBuffer[Node] {
  def &+(o: Any): NodeBuffer = {
    o match {
      case _: Unit | Text("") => // ignore
      case it: Iterator[_]    => it foreach &+
      case n: Node            => super.+=(n)
      case ns: Iterable[_]    => this &+ ns.iterator
      case ns: Array[_]       => this &+ ns.iterator
      case d                  => super.+=(new Atom(d))
    }
    this
  }
}
