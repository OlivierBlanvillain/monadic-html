package mhtml

import scala.collection.mutable.ArrayBuilder
import scala.reflect.ClassTag

private[mhtml] object buffer {
  def empty[A: ClassTag]: ArrayBuilder[A] = ArrayBuilder.make[A]

  def apply[A: ClassTag](size: Int): ArrayBuilder[A] = {
    val builder = empty[A]
    builder.sizeHint(size)
    builder
  }

  def remove[A: ClassTag](elem: A, builder: ArrayBuilder[A]): ArrayBuilder[A] = {
    val inArray: Array[A] = builder.result()
    val inArrayLen = inArray.length
    val elemIdx: Int = inArray.indexOf(elem)
    if (elemIdx >= 0 ) {
      val newBuilder = buffer[A](inArrayLen - 1)
      (0 until inArrayLen).foreach{idx =>
        if (idx != elemIdx) {
          newBuilder += inArray(idx)
        }
      }
      newBuilder
    }
    else {
      println("Warning: couldn't remove element from buffer!")
      builder
    }
  }

}
