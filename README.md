# monadic-html

[![Travis](https://travis-ci.org/OlivierBlanvillain/monadic-html.svg?branch=master)](https://travis-ci.org/OlivierBlanvillain/monadic-html) [![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/OlivierBlanvillain/monadic-html?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

Tiny DOM binding library for Scala.js

Main Objectives: friendly syntax for frontend developers (XHTML) and fast compilation speeds (no macros).

This library is inspired by [Rx.scala](https://github.com/ThoughtWorksInc/Rx.scala)
which heavily relies on macros to obtain type-safety and hide monadic context from users. [Scalatags](https://github.com/lihaoyi/scalatags) is another great library for a different approach: it defines a new type-safe DSL to write HTML.

## Design

This library uses two concepts: `Rx` and `Var`.

**`Rx[A]`** is some value of type `A` which can change over time. You can construct new bindings using `map`, `flatMap` and `filter` which will then get automatically updated when the initial `Rx` updates:

```scala
trait Rx[+A] {
  def map[B](f: A => B): Rx[B]
  def filter(f: A => Boolean): Rx[A]
  def flatMap[B](f: A => Rx[B]): Rx[B]
}
```

**`Var[A]`** extends `Rx[A]` with two additional methods, `:=` and `update`, which lets you update the value contained in the variable `Var[A]`:

```scala
class Var[A](initialValue: A) extends Rx[A] {
  def :=(newValue: A): Unit
  def update(f: A => A): Unit
}
```

The central idea is to write HTML views in term of these`Rx`s and `Var`s, such that update are automatically propagated from the source `Var`s the way to the actual DOM. The core value propagation logic was built using basic blocks from [Monix](https://github.com/monixio/monix), which makes it very reliable, pluggable to other stuff Monix based stuff such as [monixwire](https://github.com/OlivierBlanvillain/monixwire), and [out of the box testable](src/test/scala/RxTests.scala).

## Getting Started

1. Define `Var`s to store mutable data
2. Write views in a mix of XHTML and Scala expressions
3. Mount your beauty to the DOM
4. Updating `Var`s automatically propagates to the views!

```scala
import mhtml._
import scala.xml.Node
import org.scalajs.dom

val count: Var[Int] = Var[Int](0)

val dogs: Rx[Seq[Node]] =
  count.map(Seq.fill(_)(<img src="doge.png"></img>))

val component = // ← look, you can even use fancy names!
  <div style="background-color: blue;">
    <button onclick={ () => count.update(_ + 1) }>Click Me!</button>
    <p>WOW!!!</p>
    <p>MUCH REACTIVE!!!</p>
    <p>SUCH BINDING!!!</p>
    {dogs}
  </div>

val div = dom.document.createElement("div")
mount(div, component)
```

## FAQ

**Does the compiler catch HTML typos?**

No, it only rejects invalid XML literals. I've tried to explain to front-end developers the benefits of type-safety, testability and IDE support (auto-completion, inline documentation, inline error reporting) when writing frontend applications in Scala.js. This is pretty much the answer I always get:

> I make a living writing HTML & CSS. I value fast iteration speeds and using standard HTML over slow compilers and complicated IDE setups.

Hard to argue against that.

**Why Monix?**

I'm lazy (and Monix is awesome!). But keep in mind that this ain't no JS, you can depend on large libraries at no cost. Everything you don't use will get dead code eliminated.


**Global mutable state, Booo! Booo!!!**

`Var`s don't have to be globally exposed, you can instantiate them locally:

```scala
def createComponent(): xml.Node = {
  val fugitive = Var[Int](0) // It won't escape it's scope!
  myDiv(dogs(fugitive), fugitive)
}
```

Furthermore, you can restrict access to the `:=` method by using the fact that `Var[T] <: Rx[T]` as follows:

```scala
def dogs(readOnly: Rx[Int]): Rx[xml.Node] =
  <div>
    1 to readOnly map { _ =>
      <img src="doge.png"></img>
    }
  </div>
```

**How can I turn a `List[Rx[A]]` into a `Rx[List[A]]`?**

Short answer:

```scala
implicit class SequencingListFFS[A](self: List[Rx[A]]) {
  def sequence: Rx[List[A]] =
    self.foldRight(Rx(List[A]()))(for {n<-_;s<-_} yield n+:s)
}
```

[Long answer:](https://github.com/typelevel/cats/blob/master/docs/src/main/tut/traverse.md)

```scala
import cats.implicits._

implicit val RxMonadInstance: cats.Monad[Rx] = new cats.Monad[Rx] {
  def pure[A](x: A): Rx[A] = apply(x)
  def flatMap[A, B](fa: Rx[A])(f: A => Rx[B]): Rx[B] = fa.flatMap(f)
  def tailRecM[A, B](a: A)(f: (A) => Rx[Either[A, B]]): Rx[B] = defaultTailRecM(a)(f)
}
```
