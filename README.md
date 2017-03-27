# monadic-html [![Travis](https://travis-ci.org/OlivierBlanvillain/monadic-html.svg?branch=master)](https://travis-ci.org/OlivierBlanvillain/monadic-html) [![Gitter](https://badges.gitter.im/.svg)](https://gitter.im/monadic-html/Lobby) [![Maven](https://img.shields.io/maven-central/v/in.nvilla/monadic-html_sjs0.6_2.12.svg?label=maven)](https://repo1.maven.org/maven2/in/nvilla/monadic-html_sjs0.6_2.12/)

<img align=right src="project/cats.jpg"/>

Tiny DOM binding library for Scala.js

Main Objectives: friendly syntax for frontend developers (XHTML) and fast compilation speeds (no macros).

```scala
"in.nvilla" %%% "monadic-html" % "latest.integration"
```

The core value propagation library is also available separately for both platforms as `monadic-rx`. Integration with [cats](https://github.com/typelevel/cats) is optionally available as `monadic-rx-cats`.

This library is inspired by [Binding.scala](https://github.com/ThoughtWorksInc/Binding.scala) and [Scala.rx](https://github.com/lihaoyi/scala.rx) which both relies on macros to obtain type-safety and hide monadic context from users.


## Getting Started

1. Define `Var`s to store mutable state
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
    { count.map(i => if (i <= 0) <div></div> else <h2>WOW!!!</h2>) }
    { count.map(i => if (i <= 2) <div></div> else <h2>MUCH REACTIVE!!!</h2>) }
    { count.map(i => if (i <= 5) <div></div> else <h2>SUCH BINDING!!!</h2>) }
    { rxDoges }
  </div>

val div = dom.document.createElement("div")
mount(div, component)
```

For more examples, see our [test suite](tests/src/test/scala/mhtml/HtmlTests.scala), [examples](example/src/main/scala/mhtml) ([live here](https://olivierblanvillain.github.io/monadic-html/examples/)) and the [TodoMVC implementation](https://github.com/olafurpg/mhtml-todo/).


## Design

This library uses two concepts: `Rx` and `Var`.

**`Rx[A]`** is a value of type `A` which can change over time. New reactive values can be constructed with methods like `map`, `flatMap` (it's a Monad!), merge (it's a Semigroup!) [and others](#frp-ish-apis). In each case, the resulting `Rx` automatically updates when one of its constituent updates:

```scala
trait Rx[+A] {
  def map[B](f: A => B): Rx[B]
  def flatMap[B](f: A => Rx[B]): Rx[B]
  def merge(other: Rx[A]): Rx[A]
  ...
}
```

**`Var[A]`** extends `Rx[A]` with two additional methods, `:=` and `update`, which lets you update the value contained in the variable `Var[A]`:

```scala
class Var[A](initialValue: A) extends Rx[A] {
  def :=(newValue: A): Unit
  def update(f: A => A): Unit
}
```

The central idea is to write HTML views in term of these `Rx`s and `Var`s, such updates are automatically propagated from the source `Var`s all the way to the DOM. This approach, named precise data-binding* by [Binding.scala](https://github.com/ThoughtWorksInc/Binding.scala) permits DOM updates to be targeted to portions of the page affected by the change.

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
- Changing `c` will do the same between `; variable 2: ` and `.`.

These updates correspond to what React is able to compute after running its virtual-DOM diffing algorithm on the entire page. However this approach falls short when  working with large immutable data structures. Indeed, creating a large view out of a `Rx[List[_]]` implies that any changes to the `List` triggers a re-rendering of the entirety of the view. We plan to address this point in [#13](https://github.com/OlivierBlanvillain/monadic-html/issues/13) by combining the current approach with targeted virtual-DOM.


## Interacting with DOM events

Interactions with DOM events are handled using functions attached directly to xml nodes:

```scala
<button onclick={ () => println("clicked!") }>Click Me!</button>
```

Even handlers can also take one argument, which will be populated using raw event objects coming directly from the browser:

```scala
<input type="text" onchange={
  (e: js.Dynamic) =>
    val content: String = e.target.value.asInstanceOf[String]
    println(s"Input changed to: $content")
}/>
```

The function argument can be anything here, so if you're in a type safe mood feel free to use the types from [scala-js-dom](http://scala-js.github.io/scala-js-dom/):

```scala
<div onkeydown={
  (e: dom.KeyboardEvent) =>
    val code: Int = e.keyCode
    println(s"You just pressed $code")
}></div>
```

In some cases you may need to obtain references to the underlying DOM nodes your xml gets interpreted into. For this purpose, we added two new lifecycle hooks to those already available for the DOM:

- `mhtml-onmount`: called when adding a node to the DOM
- `mhtml-onunmount`: called when removing a node from the DOM

In both cases, a reference to the underlying element will be passed in to the event handler, enabling seemingless interoperability with js libraries:

```scala
def crazyCanvasStuff(e: dom.html.Canvas): Unit = ...

<canvas mhtml-onmount={ e => crazyCanvasStuff(e) }></canvas>
```

## FRP-ish APIs

This section presents the `Rx` API in its entirety. Let's start with the referentially transparent methods:

-  `def map[B](f: A => B): Rx[B]`

    Apply a function to ecah elements of this `Rx`.

    ```scala
    val numbers: Rx[Int]
    val doubles: Rx[Int] = numbers.map(2.*)
    // numbers => 0 1 4 3 2 ...
    // doubles => 0 2 8 6 4 ...
    ```

-  `def flatMap[B](f: A => Rx[B]): Rx[B]`

    Dynamically switch between different `Rx`s according to the given
    function, applied on each element of this `Rx`. Each switch will cancel
    the subscriptions for the previous outgoing `Rx` and start a new
    subscription on the next `Rx`.

    Together with `Rx#map` and `Rx.apply`, flatMap forms a `Monad`. [Proof](https://github.com/OlivierBlanvillain/monadic-html/blob/master/monadic-rx-cats/src/main/scala/mhtml/cats.scala).

-  `def product[B](other: Rx[B]): Rx[(A, B)]`

    Create the Cartesian product of two `Rx`. The output tuple contains the
    latest values from each input `Rx`, which updates whenever the value from
    either input `Rx` update. This method is faster than combining `Rx`s using
    `for { a <- ra; b <- rb } yield (a, b)`.

    ```
    // r1      => 0     8                       9     ...
    // r2      => 1           4     5     6           ...
    // product => (0,1) (8,1) (8,4) (8,5) (8,6) (9,6) ...
    ```

    This method, together with `Rx.apply`, forms am `Applicative`.
    `|@|` syntax is available via the `monadic-rx-cats` package.

-  `def dropRepeats: Rx[A]`

    Drop repeated value of this `Rx`.

    ```scala
    val numbers: Rx[Int]
    val noDups: Rx[Int] = numbers.dropRepeats
    // numbers => 0 0 3 3 5 5 5 4 ...
    // noDups  => 0   3   5     4 ...
    ```

-  `def merge(other: Rx[A]): Rx[A]`

    Merge two `Rx` into one.  Updates coming from either of the incoming `Rx`
    trigger updates in the outgoing `Rx`. Upon creation, the outgoing `Rx`
    first receives the current value from this `Rx`, then from the other `Rx`.

    ```scala
    val r1: Rx[Int]
    val r2: Rx[Int]
    val merged: Rx[Int] = r1.merge(r2)
    // r1     => 0 8     3 ...
    // r2     => 1   4 3   ...
    // merged => 0 8 4 3 3 ...
    ```

    With this operation, `Rx` forms a `Semigroup`. [Proof](https://github.com/OlivierBlanvillain/monadic-html/blob/master/monadic-rx-cats/src/main/scala/mhtml/cats.scala).
    `|+|` syntax is available via the `monadic-rx-cats` package.

-  `def foldp[B](seed: B)(step: (B, A) => B): Rx[B]`

    Produces a `Rx` containing cumulative results of applying a binary
    operator to each element of this `Rx`, starting from a `seed` and the
    current value, and moving forward in time.

    ```scala
    val numbers: Rx[Int]
    val folded: Rx[Int] = numbers.fold(0)(_ + _)
    // numbers => 1 2 1 1 3 ...
    // folded  => 1 3 4 5 8 ...
    ```

-  `def keepIf(f: A => Boolean)(a: A): Rx[A]`

    Returns a new `Rx` with updates fulfilling a predicate.
    If the first update is dropped, the default value is used instead.

    ```scala
    val numbers: Rx[Int]
    val even: Rx[Int] = numbers.keepIf(_ % 2 == 0)(-1)
    // numbers => 0 0 3 4 5 6 ...
    // even    => 0 0   4   6 ...
    ```

-  `def dropIf(f: A => Boolean)(a: A): Rx[A]`

     Returns a new `Rx` without updates fulfilling a predicate.
     If the first update is dropped, the default value is used instead.

     ```scala
     val numbers: Rx[Int]
     val even: Rx[Int] = numbers.dropIf(_ % 2 == 0)(-1)
     // numbers =>  0 0 3 4 5 6 ...
     // even    => -1   3   5   ...
     ```

-  `def sampleOn[B](other: Rx[B]): Rx[A]`

     Sample this `Rx` using another `Rx`: every time an event occurs on
     the second `Rx` the output updates with the latest value of this `Rx`.

     ```scala
     val r1: Rx[Char]
     val r2: Rx[Int]
     val sp: Rx[Int] = r2.sampleOn(r1)
     // r1 =>   u   u   u   u ...
     // r2 => 1   2 3     4   ...
     // sp =>   1   3   3   4 ...
     ```

In order to observe content of `Rx` value, we expose two methods in an `impure` object:

```scala
trait Rx[+A] {
  ...
  object impure {
    def foreach(effect: A => Unit): Cancelable
    def value: A
  }
}
```

These methods can be usefull for testing and debuging, but should ideally be avoided in application code. The `foreach` is particularly dangerous since omitting to cancel subscriptions opens the door to memory leaks. But I have good news, you don't have to use these! You should be able to do everything you need using the functional, referentially transparent APIs.

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

#### Can I `mount` a `Seq[Node]`?

Yes. It can be wrapped in a `scala.xml.Group`. One place where you might encounter this is altering the contents of the `<head>` element. This can be used to dynamically load (or unload) CSS files:

```scala
val cssUrls = Seq(
  "./target/bootstrap.min.css",
  "./target/bootstrap-theme.min.css"
)
dom.document.getElementsByTagName("head").headOption match {
  case Some(head) =>
    val linkRelCss =
      Group(cssUrls.map(cssUrl => <link rel="stylesheet" href={cssUrl}/>))
    mount(head, linkRelCss)
  case None => println("WARNING: no <head> element in enclosing document!")
}
```

#### How do I use HTML entities?

There are a couple of ways. The first is to inject it directly into a node. To get two non-breaking spaces, you might do:
`<span>&npsp;&npsp;</span>`. More generally, you will need `scala.xml.EntityRef` to reference an entity directly in Scala:

```scala
def space(nn: Int): Node = Group(Seq.fill(nn)(EntityRef("nbsp")))
<span>{space(2)}</span> // results in <span>&npsp;&npsp;</span>
```

#### Global mutable state, Booo! Booo!!!

`Vars` shouldn't be globally accessible. Instead, they should be defined and mutated as locally as possible, and exposed to the outside world as `Rxs`. In the following example uses the fact that `Var[T] <: Rx[T]` to hide the fact that `fugitive` is mutable:

```scala
def myCounter(): (xml.Node, Rx[Int]) = {
  val fugitive: Var[Int] = Var[Int](0) // It won't escape it's scope!
  val node: xml.Node =
    <div>
      <h1>So far, you clicked { fugitive } times.</h1>
      <button onclick={ () => fugitive.update(1.+) }></button>
    </div>
  (node, fugitive)
}
```

To keep your application manageable, you are advised to use exactly one local `Var` per signal coming from the outside world. Following the simple rule of "one `Var`, one `:=`" leads to clean, simple functional code. To see the difference in action, you can study [this commit](https://github.com/OlivierBlanvillain/monadic-html/commit/afb8719897812b8bdd3c649850b9b60a1cd43a17) which rewrites most of `SelectList` example from imperative to functional style.

#### How can I turn a `List[Rx[A]]` into a `Rx[List[A]]`?

Short answer:

```scala
implicit class SequencingListFFS[A](self: List[Rx[A]]) {
  def sequence: Rx[List[A]] =
    self.foldRight(Rx(List[A]()))(for {n<-_;s<-_} yield n+:s)
}
```

[Long answer:](http://typelevel.org/cats/typeclasses/traverse.html)

```scala
"in.nvilla" %%% "monadic-rx-cats" % "latest.integration"
```

```scala
import cats.implicits._, mhtml.implicits.cats._
```


## Further reading

[*Unidirectional User Interface Architectures*](http://staltz.com/unidirectional-user-interface-architectures.html) by André Staltz

This blog post presents several existing solution to handle mutable state in user interfaces. It explains the core ideas behind Flux, Redux, Elm & others, and presents a new approach, *nested dialogues*, which is similar to what you would write in `monadic-html`.

[*Controlling Time and Space: understanding the many formulations of FRP*](https://www.youtube.com/watch?v=Agu6jipKfYw) by Evan Czaplicki (author of [Elm](elm-lang.org))

This presentation gives an overview of various formulations of FRP. The talked is focused on how different systems deal with the `flatMap` operator. When combined with a `fold` operator, `flatMap` is problematic: it either leaks memory or break referential transparency. Elm solution is to simply avoid the `flatMap` operator altogether (programs can exclusively be written in applicative style).

[*Breaking down FRP*](https://blogs.janestreet.com/breaking-down-frp/) by Yaron Minsky

Blog post discussing various formulations of reactive programs. The author makes a distinction between Applicative FRP, Monadic FRP, impure Monadic FRP and Self-Adjusting Computations. The takeaway is that history-sensitivity and dynamism are competing goals: each implementations make a different trade offs.
