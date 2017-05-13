package mhtml.todo

import scala.scalajs.js.JSApp
import scala.xml.Node
import scala.collection.breakOut
import cats.implicits._
import cats.kernel.Semigroup
import mhtml._
import mhtml.implicits.cats._
import org.scalajs.dom
import org.scalajs.dom.{Event, KeyboardEvent}
import org.scalajs.dom.ext.KeyCode
import org.scalajs.dom.ext.LocalStorage
import org.scalajs.dom.raw.HTMLInputElement
import upickle.default.read
import upickle.default.write


class SemiVar[A](initialValue: Option[A], register: Var[A] => Cancelable)
  extends Var[A](initialValue, register) {

  protected var callLocations: Set[Int] = Set()
  protected def wasSet: Boolean = callLocations.size > 1
  protected def addUpdateCall(): Unit = {
    val straceSeq = new RuntimeException().getStackTrace.toSeq
    val lineNumHash = straceSeq.map(stl => stl.getLineNumber).mkString("\n").hashCode.toString
    val contentHash = straceSeq.map(
      stl => stl.getFileName + stl.getClassName + stl.getClassName
    ).mkString("\n").hashCode.toString
    val hashCode: Int = (lineNumHash + contentHash).hashCode
    callLocations += hashCode
  }


  /** Sets the value of this `Var`. Triggers recalculation of depending `Rx`s. */
  override def :=(newValue: A): Unit = {
    addUpdateCall()
    if (wasSet) throw new IllegalStateException("SemiVar was already set.")
    super.:=(newValue)
  }

  /** Updates the value of this `Var`. Triggers recalculation of depending `Rx`s. */
  override def update(f: A => A): Unit = {
    addUpdateCall()
    if (wasSet) throw new IllegalStateException("SemiVar was already set, can't update.")
    super.update(f)
  }
}

object SemiVar {
  /** Create a `SemiVar` from an initial value. */
  def apply[A](initialValue: A): SemiVar[A] =
    new SemiVar[A](Some(initialValue), _ => Cancelable.empty)
}


case class Component[D](view: Node, model: Rx[D])

case class Todo(title: String, completed: Boolean)

case class TodoList(text: String, hash: String, items: Rx[List[Todo]])

sealed trait TodoEvent
final case class UpdateEvent(oldTodo: Todo, newTodo: Todo) extends TodoEvent
final case class AddEvent(newTodo: Todo) extends TodoEvent
final case class RemovalEvent(todo: Todo) extends TodoEvent


object MhtmlTodo extends JSApp {
  val allTodos: Var[List[Todo]] = SemiVar(Nil)

  val all       = TodoList("All", "#/", allTodos)
  val active    = TodoList("Active", "#/active", allTodos.map(_.filter(!_.completed)))
  val completed = TodoList("Completed", "#/completed", allTodos.map(_.filter(_.completed)))
  val todoLists = List(all, active, completed)

  val windowHash: Rx[String] = Rx(dom.window.location.hash).merge{
    val updatedHash = Var(dom.window.location.hash)
    dom.window.onhashchange = (ev: Event) => {
      updatedHash := dom.window.location.hash
    }
    updatedHash
  }

  val currentTodoList: Rx[TodoList] = windowHash.map { hash =>
    todoLists.find(_.hash === hash).getOrElse(all)
  }

  val todoListComponents: Rx[List[Component[Option[TodoEvent]]]] =
    currentTodoList.flatMap { current =>
      current.items.map(_.map(todoListItem))
    }

  val todoListEvent: Rx[Option[TodoEvent]] = {
    val todoListModelsRx: Rx[List[Rx[Option[TodoEvent]]]] =
      todoListComponents.map(compList => compList.map(comp => comp.model))
    todoListModelsRx.flatMap(todoListModels =>
      todoListModels.foldRight[Rx[Option[TodoEvent]]](Rx(None))(
        (lastEv: Rx[Option[TodoEvent]], nextEv: Rx[Option[TodoEvent]]) => nextEv |+| lastEv
      )
    )
  }

  val header: Component[Option[AddEvent]] = {
    val newTodo = Var[Option[AddEvent]](None)
    def onInputKeydown(event: KeyboardEvent): Unit = {
      (event.currentTarget, event.keyCode) match {
        case (input: HTMLInputElement, KeyCode.Enter) =>
          input.value.trim match {
            case "" =>
            case title =>
              newTodo := Some(AddEvent(Todo(title, completed = false)))
              input.value = ""
          }
        case _ =>
      }
    }
    val headerNode =
      <header class="header">
        <h1>todos</h1>
        <input class="new-todo"
               autofocus="true"
               placeholder="What needs to be done?"
               onkeydown={onInputKeydown _}/>
      </header>
    Component(headerNode, newTodo)
  }

  val footer: Component[Option[RemovalEvent]] = {
    val removeTodo = Var[Option[RemovalEvent]](None)
    val display = allTodos.map(x => if (x.isEmpty) "none" else "")
    val visibility =
      completed.items.map(x => if (x.isEmpty) "hidden" else "visible")
    val footerNode =
      <footer class="footer" style:display={display}>
        <ul class="filters">{todoLists.map(todoListsFooter)}</ul>
        <button onclick={() =>
          allTodos.map(_.filter(_.completed).foreach(todo =>
            removeTodo := Some(RemovalEvent(todo))
          ))
          ()
          }
          class="clear-completed"
          style:visibility={visibility}>
          Clear completed
        </button>
      </footer>
    Component(footerNode, removeTodo)
  }

  val anyEvent: Rx[Option[TodoEvent]] = todoListEvent |+| footer.model |+| header.model

  val allTodosSplice: Rx[List[Todo]] =
    anyEvent.foldp(load()) { (last, ev) => updateState(last, ev) }
  val cycleRunner = allTodos |@| allTodosSplice map {
    case (oldTodos, newTodos) =>
      if (oldTodos ne newTodos) {
        println("updating all todos")
        allTodos := newTodos
      }
    <div/>
  }

  def todoListItem(todo: Todo): Component[Option[TodoEvent]] = {
    val removeTodo = Var[Option[RemovalEvent]](None)
    val updateTodo = Var[Option[UpdateEvent]](None)
    val suppressOnBlur = Var(false)
    def submit(event: Event) = {
      suppressOnBlur := true
      editingTodo := None
      event.currentTarget.asInstanceOf[HTMLInputElement].value.trim match {
        case "" =>
          removeTodo := Some(RemovalEvent(todo))
        case trimmedTitle =>
          updateTodo := Some(UpdateEvent(todo, Todo(trimmedTitle, todo.completed)))
      }
    }
    def onEditTodoTitle(event: KeyboardEvent): Unit = {
      event.keyCode match {
        case KeyCode.Escape =>
          suppressOnBlur := true
          editingTodo := None
        case KeyCode.Enter =>
          submit(event)
          focusInput()
        case _ =>
      }
    }
    def ignoreEvent(event: Event): Unit = ()
    def blurHandler: Rx[Event => Unit] =
      suppressOnBlur.map(x => if (x) ignoreEvent else submit)
    def onToggleCompleted(event: Event): Unit = {
      event.currentTarget match {
        case input: HTMLInputElement =>
          updateTodo := Some(UpdateEvent(todo, Todo(todo.title, input.checked)))
        case _ =>
      }
    }
    def onDoubleClick(event: Event): Unit = {
      editingTodo := Some(todo)
      focusInput()
    }
    val onDelete: (Event) => Unit = _ =>
      removeTodo := Some(RemovalEvent(todo))

    val css = editingTodo.map { x =>
      val editing = if (x.contains(todo)) "editing" else ""
      val completed = if (todo.completed) "completed" else ""
      s"$editing $completed"
    }
    val data: Rx[Option[TodoEvent]] = Semigroup[Rx[Option[TodoEvent]]].combine(removeTodo, updateTodo)
    val todoListElem =
      <li class={ css }>
        <div class="view">
          <input onclick={ onToggleCompleted _ }
                 class="toggle"
                 type="checkbox"
                 checked={ todo.completed } />
          <label ondblclick={ onDoubleClick _ }>{ todo.title }</label>
          <button onclick={ onDelete } class="destroy"></button>
        </div>
        <input onkeydown={ onEditTodoTitle _ }
               id="editInput"
               class="edit"
               value={ todo.title }
               onblur={ blurHandler }/>
      </li>
    Component(todoListElem, data)
  }

  val todoListElems: Rx[List[Node]] =
    todoListComponents.map{tlcSeq => tlcSeq.map(comp => comp.view)}

  val mainSection: Component[List[UpdateEvent]] = {
    val todoUpdates = Var[List[UpdateEvent]](Nil)

    def setAllCompleted(todosIn: Seq[Todo], completed: Boolean): List[UpdateEvent] =
      todosIn.flatMap{
        case todo if todo.completed != completed =>
          Some(UpdateEvent(todo, Todo(todo.title, completed)))
        case _ => None
      }(breakOut)

    // TODO(olafur) This is broken in 0.1, fix here https://github.com/OlivierBlanvillain/monadic-html/pull/9
    val checked = active.items.map(x => x.isEmpty)
    val display = allTodos.map(todos => if (todos.isEmpty) "none" else "")
    def setAllCompletedHandler(event: Event): Unit = {
      event.currentTarget match {
        case input: HTMLInputElement =>
          allTodos.map(todos => setAllCompleted(todos, input.checked))
            .map(todos => todoUpdates := todos)
          ()
        case _ => ()
      }
    }
    val mainDiv =
      <section class="main" style:display={ display }>
        <input onclick={ setAllCompletedHandler _ }
          type="checkbox"
          class="toggle-all"
          checked={ checked } />
        <label for="toggle-all" checked={ checked }>Mark all as complete</label>
        <ul class="todo-list">{ todoListElems }</ul>
      </section>
    Component(mainDiv, todoUpdates)
  }

//  object Model {
  val LocalStorageName = "todo.mhtml"

  def load(): List[Todo] =
    LocalStorage(LocalStorageName).toSeq.flatMap(read[List[Todo]]).toList

  def save(todos: List[Todo]): Unit =
    LocalStorage(LocalStorageName) = write(todos)

  val editingTodo: Var[Option[Todo]] = Var[Option[Todo]](None)

  def unzipListComponents(listComps: Seq[Component[List[TodoEvent]]])
    : (List[Node], List[Rx[List[TodoEvent]]])
    = listComps.toList.map(tlc => (tlc.view, tlc.model)).unzip

  case class ModelSources(
    headerModel: Option[Todo],
    changes:  List[TodoEvent]
  )

  def updateState(currentTodos: List[Todo], evOpt: Option[TodoEvent]): List[Todo] = evOpt match {
    case Some(AddEvent(newTodo)) => newTodo :: currentTodos
    case Some(RemovalEvent(rmTodo)) => currentTodos.filter(todo => todo == rmTodo)
    case Some(UpdateEvent(oldTodo, newTodo)) =>
      val listIndex = currentTodos.indexOf(oldTodo)
      if (listIndex > 0) {
        currentTodos.updated(listIndex, newTodo)
      }
      else currentTodos
    case None => currentTodos
  }

  def focusInput(): Unit =
    dom.document.getElementById("editInput") match {
      case t: HTMLInputElement => t.focus()
      case _ =>
    }

  val count = active.items.map { items =>
    <span class="todo-count">
      <strong>{ items.length }</strong>
      {if (items.length === 1) "item" else "items"} left
    </span>
  }

  def todoListsFooter(todoList: TodoList) = {
    val css = currentTodoList.map(x => if (x == todoList) "selected" else "")
    <li>
      <a href={ todoList.hash } class={ css }>{ todoList.text }</a>
    </li>
  }

  val todoapp: Node = {
    <div>
      { cycleRunner }
      <section class="todoapp">{ header.view }{ mainSection.view }{ footer.view }</section>
      <footer class="info">
        <p>Double-click to edit a todo</p>
        <p>
          Originally written by <a href="https://github.com/atry">Yang Bo</a>,
          adapted to monadic-html by <a href="https://github.com/olafurpg">Olafur Pall Geirsson</a>,
          rewritten to use Components by <a href="https://github.com/bbarker">Brandon Elam Barker</a>
          and <a href="https://github.com/olivierBlanvillain">Olivier Blanvillain</a>.
        </p>
        <p>Part of <a href="http://todomvc.com">TodoMVC</a></p>
      </footer>
    </div>
  }

  def main(): Unit = {
    val div = dom.document.getElementById("application-container")
    mount(div, todoapp)
    ()
  }
}
