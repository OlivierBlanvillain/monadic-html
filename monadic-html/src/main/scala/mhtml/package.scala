import scala.xml.Node

package object mhtml {
  /** Placeholder Node to not insert any value in the DOM. */
  def emptyHTML: Node = new xml.Atom(None)
}
