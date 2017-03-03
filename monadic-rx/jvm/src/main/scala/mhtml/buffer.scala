package mhtml

import collection.mutable.{ArrayBuffer => Array}

private[mhtml] object buffer {
  def empty[A]: Array[A] = new Array[A]
  def apply[A](size: Int): Array[A] = new Array(size)
}
