import scala.xml.Node

package object mhtml {
  /** Replacement for innerHTML in the DOM APT. In general, setting HTML from
    * String is risky because it's easy to inadvertently expose your users to a
    * cross-site scripting (XSS) attack. Using [[unsafeRawHTML]] in conjunction
    * with other `Atom`s in a single `Node` has undefined behavior. */
  def unsafeRawHTML(rawHtml: String): Node = new xml.Atom(UnsafeRawHTML(rawHtml))

  /** Placeholder Node to not insert any value in the DOM. */
  def emptyHTML: Node = new xml.Atom(None)
}
