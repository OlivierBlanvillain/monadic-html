package mhtml

import org.scalatest.funsuite.AnyFunSuite

class RxTests extends AnyFunSuite {
  implicit class MoreImpureStuff[A](impure: RxImpureOps[A]) {
    def value: A = {
      var v: Option[A] = None
      impure.run(a => v = Some(a)).cancel()
      // This can never happen if using the default Rx/Var constructors and
      // methods. The proof is a simple case analysis showing that every method
      // preserves non emptiness. Var created with unsafeCreate Messing up with
      // Var unsafe constructor or internal could lead to this exception.
      def error = new NoSuchElementException("Requesting value of an empty Rx.")
      v.getOrElse(throw error)
    }
  }

  test("Scala.Rx README leak") {
    var count: Int = 0
    val a: Var[Int] = Var(1)
    val b: Var[Int] = Var(2)
    def mkRx(i: Int): Rx[Int] = b.map { v => count += 1; i + v }

    val c: Rx[Int] = a.flatMap(mkRx)

    var result: (Int, Int) = null
    val cc = c.impure.run { i => result = (i, count) }

    assert((3, 1) == result)

    a := 4
    assert((6, 2) == result)

    b := 3
    assert((7, 3) == result)

    Range.inclusive(0, 100).foreach { i => a := i }
    assert((103, 104) == result)

    b := 4
    assert((104, 105) == result)

    cc.cancel()
    assert(a.isCold && b.isCold)
  }

  test("Referential transparency with map") {
    val a: Var[Int] = Var(0)
    val b: Rx[Int] = a.map(identity)
    assert(b.impure.value == 0)
    assert(a.map(identity).impure.value == 0)
    a := 1
    assert(b.impure.value == 1)
    assert(a.map(identity).impure.value == 1)
    a := 2
    assert(b.impure.value == 2)
    assert(a.map(identity).impure.value == 2)
    assert(a.isCold)
  }

  test("Verify sharing memoization and updates") {
    var count = 0
    val sourceVar = Var(0)

    // Demonstrate typical (non-shared) conditions
    val source: Rx[Int] = sourceVar.map{x => count +=1; x}
    val noshare1: Rx[Int] = source.map(identity)
    val noshare2: Rx[Int] = source.map(identity)
    val cc_ns1 = noshare1.impure.run(_ => ())
    val cc_ns2 = noshare2.impure.run(_ => ())
    assert(count == 2)
    assert(noshare1.impure.value == 0)
    sourceVar := 1
    assert(noshare1.impure.value == 1)
    assert(count == 6) // Note: impure.value calls increment also
    cc_ns1.cancel()
    cc_ns2.cancel()

    // Demonstrate sharing
    sourceVar := 0
    count = 0
    val sharedSource: Rx[Int] = sourceVar.map{x => count +=1; x}.impure.sharing
    assert(count == 0)
    val share1: Rx[Int] = sharedSource.map(identity)
    val share2: Rx[Int] = sharedSource.map(identity)
    val cc_s1 = share1.impure.run(_ => ())
    val cc_s2 = share2.impure.run(_ => ())
    assert(count == 1)
    assert(share1.impure.value == 0)
    sourceVar := 1
    assert(share1.impure.value == 1)
    assert(count == 2)
    cc_s1.cancel()
    cc_s2.cancel()

    assert(sourceVar.isCold)
  }

  test("max using foldp") {
    val value: Var[Int] = Var(0)
    val max: Rx[Int] = value.foldp(0)(_ max _)
    var current = -1
    val cc = max.impure.run(current = _)
    assert(current == 0)
    value := 3
    assert(current == 3)
    value := 2
    assert(current == 3)
    value := 5
    assert(current == 5)

    cc.cancel()
    assert(value.isCold)
  }

  test("keepIf") {
    val numbers: Var[Int] = Var(0)
    val even: Rx[Int] = numbers.keepIf(_ % 2 == 0)(-1)
    var numbersList: List[Int] = Nil
    var evenList: List[Int] = Nil
    val cc1 = numbers.impure.run(n => numbersList = numbersList :+ n)
    val cc2 = even.impure.run(n => evenList = evenList :+ n)
    numbers := 0
    numbers := 3
    numbers := 4
    numbers := 5
    numbers := 6
    assert(numbersList == List(0, 0, 3, 4, 5, 6))
    assert(evenList == List(0, 0, 4, 6))

    cc1.cancel()
    cc2.cancel()
    assert(numbers.isCold)
  }

  test("keepIf fallback") {
    val numbers: Var[Int] = Var(0)
    val empty: Rx[Int] = numbers.keepIf(_ => false)(-1)
    assert(empty.impure.value == -1)
    numbers := 1
    assert(empty.impure.value == -1)
    assert(numbers.isCold)
  }

  test("dropIf") {
    val numbers: Var[Int] = Var(0)
    val even: Rx[Int] = numbers.dropIf(_ % 2 == 0)(-1)
    var numbersList: List[Int] = Nil
    var evenList: List[Int] = Nil
    val cc1 = numbers.impure.run(n => numbersList = numbersList :+ n)
    val cc2 = even.impure.run(n => evenList = evenList :+ n)
    numbers := 0
    numbers := 3
    numbers := 4
    numbers := 5
    numbers := 6
    assert(numbersList == List(0, 0, 3, 4, 5, 6))
    assert(evenList == List(-1, 3, 5))

    cc1.cancel()
    cc2.cancel()
    assert(numbers.isCold)
  }

  test("collect") {
    val numbers: Var[Int] = Var(0)
    val even: Rx[Int] = numbers.collect { case x if x % 2 == 0 => x * 10 } (-1)
    var numbersList: List[Int] = Nil
    var evenList: List[Int] = Nil
    val cc1 = numbers.impure.run(n => numbersList = numbersList :+ n)
    val cc2 = even.impure.run(n => evenList = evenList :+ n)
    numbers := 2
    numbers := 3
    numbers := 4
    numbers := 5
    numbers := 6
    assert(numbersList == List(0, 2, 3, 4, 5, 6))
    assert(evenList == List(0, 20, 40, 60))
    cc1.cancel()
    cc2.cancel()
    assert(numbers.isCold)
  }

  test("collect fallback") {
    val numbers: Var[Int] = Var(0)
    val empty: Rx[Int] = numbers.collect { case x if false => x }(-1)
    assert(empty.impure.value == -1)
    numbers := 1
    assert(empty.impure.value == -1)
    assert(numbers.isCold)
  }

  test("dropRepeats") {
    val numbers: Var[Int] = Var(0)
    val noDups: Rx[Int] = numbers.dropRepeats
    var numbersList: List[Int] = Nil
    var noDupsList: List[Int] = Nil
    val cc1 = numbers.impure.run(n => numbersList = numbersList :+ n)
    val cc2 = noDups.impure.run(n => noDupsList = noDupsList :+ n)
    numbers := 0
    numbers := 3
    numbers := 3
    numbers := 5
    numbers := 5
    numbers := 5
    numbers := 4
    assert(numbersList == List(0, 0, 3, 3, 5, 5, 5, 4))
    assert(noDupsList == List(0, 3, 5, 4))

    cc1.cancel()
    cc2.cancel()
    assert(numbers.isCold)
  }

  test("merge") {
    val rx1: Var[Int] = Var(0)
    val rx2: Var[Int] = Var(1)
    val merged: Rx[Int] = rx1.merge(rx2)
    var rx1List: List[Int] = Nil
    var rx2List: List[Int] = Nil
    var mergedList: List[Int] = Nil
    val cc1 = rx1.impure.run(n => rx1List = rx1List :+ n)
    val cc2 = rx2.impure.run(n => rx2List = rx2List :+ n)
    val ccm = merged.impure.run(n => mergedList = mergedList :+ n)
    rx1 := 8
    rx2 := 4
    rx2 := 3
    rx1 := 3
    assert(rx1List == List(0, 8, 3))
    assert(rx2List == List(1, 4, 3))
    assert(mergedList == List(0, 1, 8, 4, 3, 3))

    cc1.cancel()
    cc2.cancel()
    ccm.cancel()
    assert(rx1.isCold && rx2.isCold)
  }

  test("merge update") {
    val rx1: Var[Int] = Var(0)
    val rx2: Var[Int] = Var(1)
    val merged: Rx[Int] = rx1.merge(rx2)
    var value = -1
    val cc = merged.impure.run(value = _)
    assert(value == 1)
    rx2 := 2
    assert(value == 2)
    rx1 := 3
    assert(value == 3)
    rx2 := 4
    assert(value == 4)

    cc.cancel()
    assert(rx1.isCold && rx2.isCold)
  }

  test("Optimisation: Applicative style is faster than monadic style.") {
    // This optimisation breaks cats laws for unpure code, but oh well...
    {
      var count = 0
      val rx1: Var[Int] = Var(1)
      val rx2: Var[Int] = Var(2)
      val rx3: Rx[Int] =
        { // That's a nice oneliner with cats' mapN
          (rx1: Rx[Int]) zip
          rx1 zip
          rx1 zip
          rx1 zip
          rx2.map { e => count = count + 1; e }
        }.map {
          case ((((i, _), _), _), j) => i + j
        }

      var list: List[Int] = Nil
      val cc = rx3.impure.run(n => list = list :+ n)
      rx1 := 3
      rx2 := 4
      assert(list == List(3, 5, 5, 5, 5, 7))
      assert(count == 2)

      cc.cancel()
      assert(rx1.isCold && rx2.isCold)
    }

    {
      var count = 0
      val rx1: Var[Int] = Var(1)
      val rx2: Var[Int] = Var(2)
      val rx2m = rx2.map { e => count = count + 1; e }

      val rx3: Rx[Int] = for {
        i <- rx1
        _ <- rx1
        _ <- rx1
        j <- rx2m
      } yield {
        i + j
      }

      var list: List[Int] = Nil
      val cc = rx3.impure.run(n => list = list :+ n)
      rx1 := 3
      rx2 := 4
      assert(list == List(3, 3, 3, 5, 7))
      assert(count == 5) // 5 > 2!

      cc.cancel()
      assert(rx1.isCold && rx2.isCold)
    }

    {
      var count = 0
      val rx1: Var[Int] = Var(1)
      val rx2: Var[Int] = Var(2)
      val rx3: Rx[Int] = for {
        i <- rx1
        _ <- rx1
        _ <- rx1
        _ <- rx1
        j <- rx2.map { e => count = count + 1; e }
      } yield i + j

      var list: List[Int] = Nil
      val cc = rx3.impure.run(n => list = list :+ n)
      rx1 := 3
      rx2 := 4
      assert(list == List(3, 3, 3, 3, 5, 7))
      assert(count == 6) // 6 > 2!

      cc.cancel()
      assert(rx1.isCold && rx2.isCold)
    }
  }

  test("zip") {
    val rx1: Var[Int] = Var(0)
    val rx2: Var[Int] = Var(1)
    val zip: Rx[(Int, Int)] = rx1.zip(rx2)
    var rx1List: List[Int] = Nil
    var rx2List: List[Int] = Nil
    var zipList: List[(Int, Int)] = Nil
    val cc1 = rx1.impure.run(n => rx1List = rx1List :+ n)
    val cc2 = rx2.impure.run(n => rx2List = rx2List :+ n)
    val ccm = zip.impure.run(n => zipList = zipList :+ n)
    rx1 := 8
    rx2 := 4
    rx2 := 5
    rx2 := 6
    rx1 := 9
    assert(rx1List == List(0, 8, 9))
    assert(rx2List == List(1, 4, 5, 6))
    assert(zipList == List((0, 1), (8, 1), (8, 4), (8, 5), (8, 6), (9, 6)))
    cc1.cancel()
    cc2.cancel()
    ccm.cancel()
    assert(rx1.isCold && rx2.isCold)
  }

  test("pile printing from README") {
    val rx1 = Var(1)
    val rx2 = Var(2)
    val rx3 =
      rx1
        .map(identity)
        .merge(
          rx2
            .map(identity)
            .dropIf(_ => false)(0)
        )
    val string = rx3.toString
    assert(
      string.contains("mhtml.RxTests$$Lambda$") || // JVM has unreliable toString
      string == """
        Merge(
          Map(Var(1), <function1>),
          Collect(
            Map(Var(2), <function1>), <function1>, 0)
        )
      """.linesIterator.mkString("").filterNot(' '.==)
    )
  }

  test("imitate") {
    def isOdd(x: Int) = x % 2 == 1

    var fstList: List[Int] = Nil
    var sndList: List[Int] = Nil

    val source = Var(0)
    val sndProxy = Var(0)
    val fst = source.merge(sndProxy.map(1.+))
    val snd = fst.keepIf(isOdd)(-101)
    val imitating = sndProxy.imitate(snd)

    assert(source.isCold && sndProxy.isCold)

    val cc1 = imitating.impure.run(_ => ())
    val cc2 = fst.impure.run(n => fstList = fstList :+ n)
    val cc3 = snd.impure.run(n => sndList = sndList :+ n)

    source := 1
    source := 6
    source := 7

    cc1.cancel()
    cc2.cancel()
    cc3.cancel()

    assert(fstList == List(0, -100, 2, 1, 6, 8, 7))
    assert(sndList == List(-101, 1, 7))
    assert(source.isCold && sndProxy.isCold)
  }

  test("no double checks with keepIf (#101)") {
    val a = Var(0)
    var checkCount = 0
    val foo = a.keepIf { i =>
      checkCount += 1
      i > 0
    }(0)

    val cc = foo.impure.run(_ => ())

    a := 1
    a := 2
    a := 3
    a := 4

    cc.cancel()

    assert(checkCount == 5)
    assert(a.isCold)
  }
}
