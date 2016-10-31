import scala.language.implicitConversions

import scala.xml.Node

package object mhtml {
  implicit def RxToOSN[A](a: Rx[A]): Node                     = new xml.Atom(a)
  implicit def Function0ToOSN(a: Function0[Unit]): Node       = new xml.Atom(a)
  implicit def Function1ToOSN[A](a: Function1[A, Unit]): Node = new xml.Atom(a)
}
