import scala.language.implicitConversions

import scala.xml.Node

package object mhtml {
  implicit def RxToOSN[A](a: Rx[A]): Node                     = new xml.Atom(a)
  implicit def Function0ToOSN(a: Function0[Unit]): Node       = new xml.Atom(a)
  implicit def Function1ToOSN[A](a: Function1[A, Unit]): Node = new xml.Atom(a)

  /** Replacement for innerHTML in the DOM APT. In general, setting HTML from
    * String is risky because it's easy to inadvertently expose your users to a
    * cross-site scripting (XSS) attack. Using [[unsafeRawHTML]] in conjunction
    * with other `Atom`s in a single `Node` has undefined behavior. */
  def unsafeRawHTML(rawHtml: String): Node = new xml.Atom(new UnsafeRawHTML(rawHtml))
}
