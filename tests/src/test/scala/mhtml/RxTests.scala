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
}
