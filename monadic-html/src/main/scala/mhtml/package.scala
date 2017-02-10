import scala.xml.Node
import org.scalajs.dom.html.Element

package object mhtml {
  /** To be used has a `mhtml-onmount` handler to set raw HTML using `.innerHTML`. */
  def setUnsafeRawHTML(a: String)(n: Element): Unit = n.innerHTML = a

  /** Placeholder Node to not insert any value in the DOM. */
  def emptyHTML: Node = new xml.Atom(None)
}
