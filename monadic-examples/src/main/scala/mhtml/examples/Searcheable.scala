package mhtml.examples

/** Typeclass for [[Chosen]] select lists */
trait Searcheable[T] {
  def show(t: T): String

  def isCandidate(query: String)(t: T): Boolean =
    show(t).toLowerCase().contains(query)
}

object Searcheable {
  def instance[T](f: T => String): Searcheable[T] = new Searcheable[T] {
    override def show(t: T): String = f(t)
  }
  implicit val stringSearchable: Searcheable[String] =
    instance[String](identity)
}