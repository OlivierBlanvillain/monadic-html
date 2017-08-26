package mhtml

import scalajs.js.Array

private[mhtml] object buffer {
  type Buffer[E] = Array[E]
  def empty[A]: Buffer[A] = new Array[A]
  def apply[A](size: Int): Buffer[A] = new Array(size)
}
