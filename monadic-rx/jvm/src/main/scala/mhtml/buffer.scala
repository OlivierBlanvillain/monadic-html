package mhtml

private[mhtml] object buffer {
  def empty[A] = collection.mutable.ArrayBuffer.empty[A]
}
