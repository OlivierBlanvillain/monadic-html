package mhtml

import collection.mutable.ArrayBuffer

private[mhtml] object buffer {
  def empty[A]: ArrayBuffer[A] =
    ArrayBuffer.empty[A]

  def apply[A](size: Int): ArrayBuffer[A] =
    ArrayBuffer.fill(size)(null.asInstanceOf[A])
}
