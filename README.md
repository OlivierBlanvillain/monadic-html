# monadic-html [![Travis](https://travis-ci.org/OlivierBlanvillain/monadic-html.svg?branch=master)](https://travis-ci.org/OlivierBlanvillain/monadic-html) [![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/monadic-html/Lobby) [![Maven](https://img.shields.io/maven-central/v/in.nvilla/monadic-html_sjs0.6_2.11.svg?label=maven)](http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22in.nvilla%22%20AND%20a%3A%22monadic-html_sjs0.6_2.11%22)

<img align=right src="project/cats.jpg"/>

Tiny DOM binding library for Scala.js

Main Objectives: friendly syntax for frontend developers (XHTML) and fast compilation speeds (no macros).

```scala
"in.nvilla" %%% "monadic-html" % "latest.integration"
```

The core value propagation library is also available separately for both platforms as `monadic-rx`. Integration with [cats](https://github.com/typelevel/cats) is optionally available as `monadic-rx-cats`.

This library is inspired by [Binding.scala](https://github.com/ThoughtWorksInc/Binding.scala) and [Scala.rx](https://github.com/lihaoyi/scala.rx) which both relies on macros to obtain type-safety and hide monadic context from users.

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
  count.map(i => Seq.fill(i)(<img src="doge.png"></img>))

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

For more examples, see our [test suite](tests/src/test/scala/mhtml/tests.scala), [examples](example/src/main/scala/mhtml) ([live here](https://olivierblanvillain.github.io/monadic-html/examples/)) and the [TodoMVC implementation](https://github.com/olafurpg/mhtml-todo/).

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

The central idea is to write HTML views in term of these`Rx`s and `Var`s, such that update are automatically propagated from the source `Var`s the way to the actual DOM.

## FAQ

**Does the compiler catch HTML typos?**

No, only invalid XML literals will be rejected at compile time. However, we do provide [configurable settings](src/main/scala/mhtml/settings.scala) to emit runtime warnings about unknown elements, attributes, entity references and event handlers. For example, the following piece of XML compiles fine:

```scala
<captain yolo="true" onClick={ () => println("Oh yeah!") }>{None}</captain>
```

But mounting it to the DOM will print warnings in the console:

```
[mhtml] Warning: Implicitly converted class scala.None$ to it's string representation: "None". Call toString explicitly to remove this warning.
[mhtml] Warning: Unknown event onClick. Did you mean onclick instead?
[mhtml] Warning: Unknown attribute yolo. Did you mean cols instead?
[mhtml] Warning: Unknown element captain. Did you mean caption instead?
```

`MountSettings.default` emit warnings only when compiled to with `fastOptJS`, and becomes silent (and faster) whe compiled with `fullOptJS`.

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

In an ideal world, you would use exactly one `Rx` per signal coming from the outside world, which means using a single `:=` per `Rx`. Doing so leads to a very nice functional reactive programming style code, analogous to what you would write in [Elm](http://elm-lang.org/).

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
"in.nvilla" %%% "monadic-rx-cats" % "latest.integration"
```

```scala
import cats.implicits._, mhtml.cats._
```
