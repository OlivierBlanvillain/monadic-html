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

val count: Var[Int] = Var(0)

val doge: Node =
  <img style="width: 100px;" src="http://doge2048.com/meta/doge-600.png"/>

val rxDoges: Rx[Seq[Node]] =
  count.map(i => Seq.fill(i)(doge))

val component = // ← look, you can even use fancy names!
  <div>
    <button onclick={ () => count.update(_ + 1) }>Click Me!</button>
    {count.map(i => if (i <= 0) <div></div> else <h2>WOW!!!</h2>)}
    {count.map(i => if (i <= 2) <div></div> else <h2>MUCH REACTIVE!!!</h2>)}
    {count.map(i => if (i <= 5) <div></div> else <h2>SUCH BINDING!!!</h2>)}
    {rxDoges}
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

The central idea is to write HTML views in term of these`Rx`s and `Var`s, such that update are automatically propagated from the source `Var`s the way to the DOM.

This approach, cleverly called *precise data-binding* in [Binding.scala](https://github.com/ThoughtWorksInc/Binding.scala), has the same goal than the virtual-DOM technique employed in React: minimize the amount of DOM updates. Thanks to the separation between definition and mounting, `monadic-html` is able to propagate `Var` updates to the DOM without doing any complex computations (no diff!), while still restricting DOM updates to the parts of the page affected by the update.

Let's look at a concrete example:

```scala
val a = Var("id1")
val b = Var("foo")
val c = Var("bar")

val view =
  <div id={a}>
    Variable 1: {b}; variable 2: {c}.
  </div>
```

When mounting this view, [the implementation](https://github.com/OlivierBlanvillain/monadic-html/blob/master/monadic-html/src/main/scala/mhtml/mount.scala) will attach callbacks to each `Rx` such that changing `a`, `b` or `c` results in precise DOM updates:

- Changing `a` will update the `div` attribute (reusing the same `div` node)
- Changing `b` will delete the text node between `Variable 1: ` and `; variable 2: `, and insert a new replacement between these two nodes.
- Changing `c` will do the same, except between `; variable 2: ` and `.`.

These updates correspond to what React would compute using virtual-DOM diffing.

When working with large immutable data structures, this approach is less performant than virtual-DOM diffing. Indeed, creating a large view out of a `Rx[List[_]]` implies that any changes to the `List` triggers a re-rendering of the entirety of the view. We plan to address this point in [#13](https://github.com/OlivierBlanvillain/monadic-html/issues/13) by combining the current approach with targeted virtual-DOM.

## Interacting with js events

Interactions with the imperative world of js events in done by attaching even handlers directly to xml nodes:

```scala
<button onclick={ () => println("clicked!") }>Click Me!</button>
```

Even handlers can also take one argument, in which case they will be called with raw event objects coming directly from the js world:

```scala
<div onkeydown={ (e: js.Dynamic) => () }></div>
```

The function argument can be anything here, so if you're in a type safe mood feel free to use [scala-js-dom](http://scala-js.github.io/scala-js-dom/):

```scala
<div onkeydown={ (e: dom.KeyboardEvent) => println(e.keyCode) }></div>
```

Note that the idiomatic way to handle events is to isolate side effects by opting into the `Rx` world as soon as possible. For example:

```scala
def button(text: String): (xml.Node, Rx[Unit]) = {
  val clicked: Var[Unit] = Var(())
  val button = <button onclick={ () => clicked := (()) }>{text}</button>
  (button, clicked)
}
```

## Interacting with the DOM

In order to obtain references to the underlying DOM nodes, `monadic-html` implements lifecycle hooks using custom events:

- `mhtml-onmount`: called when adding a node to the DOM
- `mhtml-onunmount`: called when removing a node from the DOM

In both cases, a reference to the underlying element is passed to the event hander, which can be usefull for interoperability with js libraries:

```scala
def crazyCanvasStuff(e: dom.html.Canvas): Unit = ...

<canvas mhtml-onmount={ e => crazyCanvasStuff(e) }></canvas>
```

## FAQ

#### Does the compiler catch HTML typos?

No, only invalid XML literals will be rejected at compile time. However, we do provide [configurable settings](src/main/scala/mhtml/settings.scala) to emit runtime warnings about unknown elements, attributes, entity references and event handlers. For example, the following piece of XML compiles fine:

```scala
<captain yolo="true" onClick={ () => println("Oh yeah!") }></captain>
```

But mounting it to the DOM will print warnings in the console:

```
[mhtml] Warning: Unknown event onClick. Did you mean onclick instead?
[mhtml] Warning: Unknown attribute yolo. Did you mean cols instead?
[mhtml] Warning: Unknown element captain. Did you mean caption instead?
```

`MountSettings.default` emit warnings only when compiled to with `fastOptJS`, and becomes silent (and faster) whe compiled with `fullOptJS`.

#### Can I insert Any values into xml literals?

No. Monadic-html uses a fork of scala-xml that puts type constraints on
what values are allowed in xml element or attribute position.
Here is a summary of what types are allowed to be embedded in attribute or
element position.

Both attributes and elements:

- `String`
- `mhtml.Var[T], mhtml.Rx[T] where T is itself embeddable`
- `Option[T] where T can itself be embedded (None → remove from the DOM)`

Attributes:

- `Boolean (false → remove from the DOM)`
- `() => Unit, T => Unit event handler`

Elements:

- `Int, Long, Double, Float, Char (silently converted with .toString)`
- `xml.Node`
- `Seq[xml.Node]`

For examples of how each type is rendered into dom nodes, take a look at the
[tests](https://github.com/OlivierBlanvillain/monadic-html/blob/master/tests/src/test/scala/mhtml/RenderTests.scala).

#### Global mutable state, Booo! Booo!!!

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

#### How can I turn a `List[Rx[A]]` into a `Rx[List[A]]`?

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
import cats.implicits._, mhtml.implicits.cats._
```

## Further reading

[*Unidirectional User Interface Architectures*](http://staltz.com/unidirectional-user-interface-architectures.html) by André Staltz

This blog post presents several existing solution to handle mutable state in user interfaces. It explains the core ideas behind Flux, Redux, Elm & others, and presents a new approach, *nested dialogues*, which is similar to what you would write in `monadic-html`.

[*Controlling Time and Space: understanding the many formulations of FRP*](https://www.youtube.com/watch?v=Agu6jipKfYw) by Evan Czaplicki

This presentation gives an overview of various formulations of FRP. The talked is focused on how different systems deal with the `flatMap` operator. When combined with a `fold` operator, `flatMap` is problematic: it either leaks memory or break referential transparency. Elm solution is to simply avoid the `flatMap` operator altogether (programs can exclusively be written using the "applicative style"). `monadic-html` takes the opposite approach of not exposing a `fold`.
