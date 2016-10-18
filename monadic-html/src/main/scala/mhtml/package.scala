import scala.language.implicitConversions
import org.scalajs.dom

package object mhtml {
  // Node: This really needs to be `Some(List(.)))` because of the `_ :: _` pattern matching in Rx.scala.
  private def wrap[A](a: A): Option[Seq[xml.Node]] = Some(List(new xml.Atom(a)))

  implicit def RxToOSN[A](a: Rx[A]) = wrap(a)
  implicit def Function0ToOSN(a: Function0[Unit]) = wrap(a)
  implicit def Function1ToOSN[A <: dom.Event](a: Function1[A, Unit]) = wrap(a)
}
