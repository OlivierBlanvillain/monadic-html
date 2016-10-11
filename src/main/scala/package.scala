import scala.language.implicitConversions

package object mhtml {
  // Node: this really needs to be a List allow pattern matching on it in Binding.scala
  implicit def BindingToOptionSeqNode[A](b: Binding[A]): Option[Seq[xml.Node]] =
    Some(List(new xml.Atom(b)))
}
