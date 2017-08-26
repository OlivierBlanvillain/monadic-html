package mhtml

import collection.mutable.ArrayBuffer

private[mhtml] object buffer {
  type Buffer[E] = ArrayBuffer[E]
  def empty[A]: Buffer[A] =
    ArrayBuffer.empty[A]

  def apply[A](size: Int): Buffer[A] =
    ArrayBuffer.fill(size)(null.asInstanceOf[A])
}
