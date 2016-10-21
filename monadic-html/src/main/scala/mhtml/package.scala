import scala.language.implicitConversions

package object mhtml {
  implicit def RxToOSN[A](a: Rx[A])                     = new xml.Atom(a)
  implicit def Function0ToOSN(a: Function0[Unit])       = new xml.Atom(a)
  implicit def Function1ToOSN[A](a: Function1[A, Unit]) = new xml.Atom(a)
}
