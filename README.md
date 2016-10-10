# Monixbinding

This is sub 100 lines Scala.js alternative.

- Native / naive XHTML syntax (not type safe)
- Simplistic design

## How does it work?

1. Define `Var`s to store mutable data
2. Write your views in a mix of XHTML (not typed check) and Scala expressions
3. Mount your beauty to the DOM, things automatically update when `Var`s are updated!

```scala
import monixbinding._
import scala.xml.Node

val count: Var[Int] = Var[Int](0)

val dogs: Binding[Seq[Node]] =
  1 to count map { _ =>
    <img src="doge.png"></img>
  }

val component: Node = // ← look, you can also use fancy name!
  <div style="background-color: blue;">
    <p>WOW!!!</p>
    <p>MUCH REACTIVE!!!</p>
    <p>SUCH BINDING!!!</p>
    {dogs}
  </div>

val div = org.scalajs.dom.document.createElement("div")
mount(div, component)
```

That's all there is to it. Enjoy


## FAQ

**Why no type safety?**

I've tried to explain to front-end developers how much type-safety, IDE support (auto-completion, inlined documentation, inline error reporting), and testability they would gain from using Scala.js and libraries such as [scala-tags](), [scala-css]() & other. This is basically what I've got:

> I'm made a leaving off writing HTML & CSS by hand, I don't want to/care about learning better ways to do my job. Go back wasting your time compiling code and running tests in your 4GB consuming IDE, and leave me alone with my stuff.

Hard to argue anything against that. (Statement obviously over exaggerated for the purpose of this FAQ)

**Why Monix?**

I'm lazy (and Monix is awesome!). But keep in mind that this ain't no JS, you can depend on large libraries at no cost. Everything you don't use gets dead code eliminated and minimized together.

**Global mutable state, Booo! Booo!**

`Var`s don't have to be globally exposed, you can instantiate them super very locally:

```scala
def createComponent(): xml.Node = {
  val fugitive = Var[Int](0) // It won't escape it's scope!
  myDiv(dogs(fugitive), fugitive)
}
```

Furthermore, you can restrict access to the `:=` method by using `Var[T] <: Binding[T]` as follows:

```scala
def dogs(readOnly: Binding[Int]): Binding[xml.Node] =
  <div>
    1 to readOnly map { _ =>
      <img src="doge.png"></img>
    }
  </div>
```
