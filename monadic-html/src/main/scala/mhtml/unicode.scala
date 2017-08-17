package mhtml

import scala.scalajs.js
import scala.scalajs.js.annotation._

private[mhtml] object unicode {

  // Note: NativeJSString and fromCodePoint are replicas of private
  // methods/objects in Scala.JS

  @js.native
  @JSGlobal("String")
  private object NativeJSString extends js.Object {
    def fromCharCode(charCodes: Int*): String = js.native
  }
  def fromCodePoint(codePoint: Int): String = {
    if ((codePoint & ~Character.MAX_VALUE) == 0) {
      NativeJSString.fromCharCode(codePoint)
    } else if (codePoint < 0 || codePoint > Character.MAX_CODE_POINT) {
      throw new IllegalArgumentException
    } else {
      val offsetCp = codePoint - 0x10000
      NativeJSString.fromCharCode(
        (offsetCp >> 10) | 0xd800, (offsetCp & 0x3ff) | 0xdc00)
    }
  }
}
