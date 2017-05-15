package mhtml

import cats.implicits._
import mhtml.implicits.cats._

import org.scalatest.FunSuite

class RxCatsTests extends FunSuite {

  test("cyclic_update: simple list") {
    val value: Var[Int] = Var(0)
    val valueList: Var[List[Int]] = Var(Nil)
    val valuesRunner = value |@| valueList map {
      case (newInt, oldList) =>
        val newList = List(newInt)
        println(s"oldcomps = $oldList;\nnewcomps= $newList\n${oldList == newList}") // DEBUG
        if (oldList != newList) {
          valueList := newList
        }
    }
    val cc = valuesRunner.impure.foreach(x => ())
    valueList.map(list => assert(list == Nil))
    value := 3
    valueList.map(list => assert(list.head == 3))
    value := 2
    valueList.map(list => assert(list.head == 2))
    value := 5
    valueList.map(list => assert(list.head == 5))

    cc.cancel
    assert(value.isHot)
  }


}
