package mhtml

import scala.scalajs.js.timers._
import org.scalatest.FunSuite

class RxJsTests extends FunSuite with RxTestHelpers {

  test("merge registrations are tail-shareable") {

    val delay: Double = 25 // ms

    val rx1: Var[Int] = Var(0)
    val rx2: Var[Int] = Var(1)
    val merged: Rx[Int] = rx1.merge(rx2)
    var rx1List: List[Int] = Nil
    var rx2List: List[Int] = Nil
    var merged1List: List[Int] = Nil
    var merged2List: List[Int] = Nil
    val cc1 = rx1.impure.run(n => rx1List = rx1List :+ n)
    val cc2 = rx2.impure.run(n => rx2List = rx2List :+ n)
    var ccm1: Cancelable = Cancelable.empty
    var ccm2: Cancelable = Cancelable.empty
    setTimeout(1 * delay){
      rx1 := 0
      rx2 := 1
    }
    setTimeout(2 * delay){
      ccm1 = merged.impure.run(n => merged1List = merged1List :+ n)
    }
    setTimeout(3 * delay){
      rx1 := 8
    }
    setTimeout(4 * delay){
      rx2 := 4
    }
    setTimeout(5 * delay){
      ccm2 = merged.impure.run(n => merged2List = merged2List :+ n)
    }
    setTimeout(6 * delay){
      rx2 := 3
    }
    setTimeout(7 * delay){
      rx1 := 3
    }
    setTimeout(8 * delay) {
      assert(rx1List == List(0, 8, 3))
      assert(rx2List == List(1, 4, 3))
      assert(merged1List == List(0, 8, 4, 3, 3))
      assert(merged2List == List(8, 3, 3))

      cc1.cancel
      cc2.cancel
      ccm1.cancel
      ccm2.cancel
      assert(rx1.isCold && rx2.isCold)
    }
  }

}
