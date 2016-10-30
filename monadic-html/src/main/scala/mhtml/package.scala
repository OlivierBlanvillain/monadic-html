import scala.language.implicitConversions

import scala.xml.Node

package object mhtml {
//  implicit def OptionToOSN[A](a: Option[A]): Option[Node]          = a.map(x => new Atom(x))
  implicit def RxToOSN[A](a: Rx[A]): Seq[Node]                     = new xml.Atom(a)
  implicit def Function0ToOSN(a: Function0[Unit]): Seq[Node]       = new xml.Atom(a)
  implicit def Function1ToOSN[A](a: Function1[A, Unit]): Seq[Node] = new xml.Atom(a)
}
