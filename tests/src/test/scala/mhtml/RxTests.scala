package mhtml

import org.scalatest.FunSuite

class RxTests extends FunSuite {
  test("Scala.Rx README router") {
    var fakeTime: Int = 123

    trait WebPage {
      def fTime: Int = fakeTime
      val time: Var[Int] = Var(fTime)
      def update(): Unit = time := fTime
      val html: Rx[String]
    }

    class HomePage extends WebPage {
      val html: Rx[String] = time.map(s"Home Page! time: ".+)
    }

    class AboutPage extends WebPage {
      val html: Rx[String] = time.map(s"About Me, time: ".+)
    }

    val url = Var("www.mysite.com/home")

    val page: Rx[WebPage] =
      url.map {
        case "www.mysite.com/home"  => new HomePage()
        case "www.mysite.com/about" => new AboutPage()
      }

    var result: String = ""
    page.foreach { p => p.html.foreach(x => result = x); () }
    assert(result == "Home Page! time: 123")

    fakeTime = 234
    page.value.update()
    assert(result == "Home Page! time: 234")

    fakeTime = 345
    url := "www.mysite.com/about"
    assert(result == "About Me, time: 345")

    fakeTime = 456
    page.value.update()
    assert(result == "About Me, time: 456")
  }

  test("Scala.Rx README leak") {
    var count: Int = 0
    val a: Var[Int] = Var(1)
    val b: Var[Int] = Var(2)
    def mkRx(i: Int): Rx[Int] = b.map { v => count += 1; i + v }

    val c: Rx[Int] = a.flatMap(mkRx)

    var result: (Int, Int) = null
    c.foreach { i => result = (i, count) }

    assert((3, 1) == result)

    a := 4
    assert((6, 2) == result)

    b := 3
    assert((7, 3) == result)

    Range.inclusive(0, 100).foreach { i => a := i }
    assert((103, 104) == result)

    b := 4
    assert((104, 105) == result)
  }

  test("Referential transparency with map") {
    val a: Var[Int] = Var(0)
    val b: Rx[Int] = a.map(identity)
    assert(b.value == 0)
    assert(a.map(identity).value == 0)
    a := 1
    assert(b.value == 1)
    assert(a.map(identity).value == 1)
    a := 2
    assert(b.value == 2)
    assert(a.map(identity).value == 2)
  }

  test("Flatmap is optimised for applicative style") {
    var cancels: Int = 0
    val r1: Var[Int] = Var(0)
    val r2: Var[Int] = Var.create[Int](0)(_ => Cancelable(() => cancels += 1))
    val out: Rx[Int] = r1.flatMap(_ => r2)
    out.foreach(_ => ())
    r1 := 1
    assert(cancels == 0)
    assert(out.value == 0)
    r2 := 1
    assert(cancels == 0)
    assert(out.value == 1)
  }

  test("max using foldp") {
    val value: Var[Int] = Var(0)
    val max: Rx[Int] = value.foldp(0)(_ max _)

    max.foreach(_ => ())

    assert(max.value == 0)
    value := 3
    assert(max.value == 3)
    value := 2
    assert(max.value == 3)
    value := 5
    assert(max.value == 5)
  }

  test("foldp is not referentially transparent") {
    // This is the counter example from Controlling Time and Space:
    // understanding the many formulations of FRP.

    val clicks: Var[Unit] = Var(())

    val clickCount: Rx[Int] =
      clicks.foldp(0) { case (_, c: Int) => c + 1 }

    clickCount.foreach(_ => ())

    assert(clickCount.value == 1)
    clicks := (())
    assert(clickCount.value == 2)
    clicks := (())
    assert(clickCount.value == 3)

    def clicksOrZero(c: Boolean): Rx[Int] =
      if (c) clickCount
      else Rx(0)

    val t1 = clicksOrZero(false)
    assert(t1.value == 0)

    val t2 = clicksOrZero(true)
    assert(t2.value == 3)

    // Evidence that foldp expressions (clickCount) are not referentially
    // transparent with dynamic FRP (with bounded memory):

    def clicksOrZero2(c: Boolean): Rx[Int] =
      if (c) clicks.foldp(0) { case (_, c: Int) => c + 1 }
      else Rx(0)

    val t3 = clicksOrZero2(false)
    assert(t3.value == 0)

    val t4 = clicksOrZero2(true)
    assert(t4.value == 1) // This was == 3 before
  }

  test("keepIf") {
    val numbers: Var[Int] = Var(0)
    val even: Rx[Int] = numbers.keepIf(_ % 2 == 0)(-1)
    var numbersList: List[Int] = Nil
    var evenList: List[Int] = Nil
    numbers.foreach(n => numbersList = numbersList :+ n)
    even.foreach(n => evenList = evenList :+ n)
    numbers := 0
    numbers := 3
    numbers := 4
    numbers := 5
    numbers := 6
    assert(numbersList == List(0, 0, 3, 4, 5, 6))
    assert(evenList == List(0, 0, 4, 6))
  }

  test("keepIf fallback") {
    val numbers: Var[Int] = Var(0)
    val empty: Rx[Int] = numbers.keepIf(_ => false)(-1)
    assert(empty.value == -1)
    numbers := 1
    assert(empty.value == -1)
  }

  test("dropIf") {
    val numbers: Var[Int] = Var(0)
    val even: Rx[Int] = numbers.dropIf(_ % 2 == 0)(-1)
    var numbersList: List[Int] = Nil
    var evenList: List[Int] = Nil
    numbers.foreach(n => numbersList = numbersList :+ n)
    even.foreach(n => evenList = evenList :+ n)
    numbers := 0
    numbers := 3
    numbers := 4
    numbers := 5
    numbers := 6
    assert(numbersList == List(0, 0, 3, 4, 5, 6))
    assert(evenList == List(-1, 3, 5))
  }

  test("collect") {
    val numbers: Var[Int] = Var(0)
    val even: Rx[Int] = numbers.collect { case x if x % 2 == 0 => x * 10 } (-1)
    var numbersList: List[Int] = Nil
    var evenList: List[Int] = Nil
    numbers.foreach(n => numbersList = numbersList :+ n)
    even.foreach(n => evenList = evenList :+ n)
    numbers := 2
    numbers := 3
    numbers := 4
    numbers := 5
    numbers := 6
    assert(numbersList == List(0, 2, 3, 4, 5, 6))
    assert(evenList == List(0, 20, 40, 60))
  }

  test("collect fallback") {
    val numbers: Var[Int] = Var(0)
    val empty: Rx[Int] = numbers.collect { case x if false => x }(-1)
    assert(empty.value == -1)
    numbers := 1
    assert(empty.value == -1)
  }

  test("dropRepeats") {
    val numbers: Var[Int] = Var(0)
    val noDups: Rx[Int] = numbers.dropRepeats
    var numbersList: List[Int] = Nil
    var noDupsList: List[Int] = Nil
    numbers.foreach(n => numbersList = numbersList :+ n)
    noDups.foreach(n => noDupsList = noDupsList :+ n)
    numbers := 0
    numbers := 3
    numbers := 3
    numbers := 5
    numbers := 5
    numbers := 5
    numbers := 4
    assert(numbersList == List(0, 0, 3, 3, 5, 5, 5, 4))
    assert(noDupsList == List(0, 3, 5, 4))
  }

  test("merge") {
    val r1: Var[Int] = Var(0)
    val r2: Var[Int] = Var(1)
    val merged: Rx[Int] = r1.merge(r2)
    var r1List: List[Int] = Nil
    var r2List: List[Int] = Nil
    var mergedList: List[Int] = Nil
    r1.foreach(n => r1List = r1List :+ n)
    r2.foreach(n => r2List = r2List :+ n)
    merged.foreach(n => mergedList = mergedList :+ n)
    r1 := 8
    r2 := 4
    r2 := 3
    r1 := 3
    assert(r1List == List(0, 8, 3))
    assert(r2List == List(1, 4, 3))
    assert(mergedList == List(0, 8, 4, 3, 3))
  }

  test("merge update") {
    val r1: Var[Int] = Var(0)
    val r2: Var[Int] = Var(1)
    val merged: Rx[Int] = r1.merge(r2)
    merged.foreach(_ => ())
    assert(merged.value == 0)
    r2 := 2
    assert(merged.value == 2)
    r1 := 3
    assert(merged.value == 3)
    r2 := 4
    assert(merged.value == 4)
  }
}
