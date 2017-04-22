package mhtml.todo

import scala.scalajs.js.JSApp
import scala.xml.Node
import scala.collection.breakOut
import cats.implicits._
import mhtml._
import mhtml.implicits.cats._
import org.scalajs.dom
import org.scalajs.dom.{Event, HashChangeEvent, KeyboardEvent}
import org.scalajs.dom.ext.KeyCode
import org.scalajs.dom.ext.LocalStorage
import org.scalajs.dom.raw.HTMLInputElement
import upickle.default.read
import upickle.default.write

import scala.scalajs.runtime.UndefinedBehaviorError

object MhtmlTodo extends JSApp {

  case class Component[D](view: Node, model: Rx[D])

  case class Todo(title: String, completed: Boolean)

  case class TodoList(text: String, hash: String, items: Rx[Seq[Todo]])

  //
  // Add components for the app
  //

  val header: Component[Option[Todo]] = {
    val newTodo = Var[Option[Todo]](None)
    def onInputKeydown(event: KeyboardEvent): Unit = {
      (event.currentTarget, event.keyCode) match {
        case (input: HTMLInputElement, KeyCode.Enter) =>
          input.value.trim match {
            case "" =>
            case title =>
              newTodo := Some(Todo(title, completed = false))
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

  def footer: Component[List[Todo]] = {
    val removeTodos = Var[List[Todo]](Nil)
    val display = allTodos.map(x => if (x.isEmpty) "none" else "")
    val visibility =
      completed.items.map(x => if (x.isEmpty) "hidden" else "visible")
    val footerNode =
      <footer class="footer" style:display={display}>
        <ul class="filters">{todoLists.map(todoListsFooter)}</ul>
        <button onclick={() =>
            allTodos.map(_.filter(_.completed).foreach(todo =>
              removeTodos.update(rtList => todo :: rtList)
            ))
            ()
          }
          class="clear-completed"
          style:visibility={visibility}>
          Clear completed
        </button>
      </footer>
    Component(footerNode, removeTodos)
  }

  case class TodoUpdate(oldTodo: Todo, newTodo: Todo)
  case class TodoListItemData(removal: Option[Todo], update: Option[TodoUpdate])
  //
  def todoListItem(todo: Todo): Component[TodoListItemData] = {
    val removeTodo = Var[Option[Todo]](None)
    val updateTodo = Var[Option[TodoUpdate]](None)
    val suppressOnBlur = Var(false)
    def submit(event: Event) = {
      suppressOnBlur := true
      editingTodo := None
      event.currentTarget.asInstanceOf[HTMLInputElement].value.trim match {
        case "" =>
          removeTodo := Some(todo)
        case trimmedTitle =>
          updateTodo := Some(TodoUpdate(todo, Todo(trimmedTitle, todo.completed)))
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
          updateTodo := Some(TodoUpdate(todo, Todo(todo.title, input.checked)))
        case _ =>
      }
    }
    def onDoubleClick(event: Event): Unit = {
      editingTodo := Some(todo)
      focusInput()
    }
    val onDelete: (Event) => Unit = _ =>
      removeTodo := Some(todo)

    val css = editingTodo.map { x =>
      val editing = if (x.contains(todo)) "editing" else ""
      val completed = if (todo.completed) "completed" else ""
      s"$editing $completed"
    }
    val data = (removeTodo |@| updateTodo).map {
      case (rmTodo: Option[Todo], update: Option[TodoUpdate]) =>
        TodoListItemData(rmTodo, update)
    }

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


  def mainSection: Component[List[TodoUpdate]] = {

    val todoUpdates = Var[List[TodoUpdate]](Nil)

    def setAllCompleted(todosIn: Seq[Todo], completed: Boolean): List[TodoUpdate] =
      todosIn.flatMap{
        case todo if todo.completed != completed =>
          Some(TodoUpdate(todo, Todo(todo.title, completed)))
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
  def load(): Seq[Todo] =
    LocalStorage(LocalStorageName).toSeq.flatMap(read[Seq[Todo]])
  def save(todos: Seq[Todo]): Unit =
    LocalStorage(LocalStorageName) = write(todos)
  val windowHash: Rx[String] = Rx(dom.window.location.hash).merge{
    var updatedHash = Var(dom.window.location.hash)
    dom.window.onhashchange = (ev: HashChangeEvent) => {
      updatedHash := dom.window.location.hash
    }
    updatedHash
  }

  val editingTodo: Var[Option[Todo]] = Var[Option[Todo]](None)
  val all = TodoList("All", "#/", allTodos)
  val active =
    TodoList("Active", "#/active", allTodos.map(_.filter(!_.completed)))
  val completed =
    TodoList("Completed", "#/completed", allTodos.map(_.filter(_.completed)))
  val todoLists = Seq(all, active, completed)


  val currentTodoList: Rx[TodoList] = windowHash.map(hash =>
    todoLists.find(_.hash === hash).getOrElse(all)
  )
  val todoListComponents: Rx[Seq[Component[TodoListItemData]]] =
    currentTodoList.flatMap { current =>
      current.items.map(_.map(todoListItem))
    }

  // Note: Cats Traverse can't support Seq, so we use List
  def unzipListComponents(listComps: Seq[Component[TodoListItemData]])
  : (List[Node], List[Rx[TodoListItemData]])
  = listComps.toList.map(tlc => (tlc.view, tlc.model)).unzip

  val todoListElems: Rx[List[Node]] =
    todoListComponents.map{tlcSeq => unzipListComponents(tlcSeq)._1}

  val todoListDataChanges: Rx[List[TodoListItemData]] =
    todoListComponents.flatMap{tlcSeq => unzipListComponents(tlcSeq)._2.sequence}

//  lazy val allTodos: Rx[Seq[Todo]] = (
//    Rx(load()) |@| header.model |@| todoListDataChanges
//  ) map {
//    case (stored: Seq[Todo], newTodoMaybe: Option[Todo], changes: List[TodoListItemData]) =>
//      val removals: List[Todo] = changes.flatMap(change => change.removal)
//      val updates: List[TodoUpdate] = changes.flatMap(change => change.update)
//
//      (stored ++ newTodoMaybe).filter(todo => !removals .contains(todo))
//      .map{todo => updates.find{update => update.oldTodo == todo} match {
//        case Some(foundUpdate) => foundUpdate.newTodo
//        case None => todo
//      }}
//  }

  case class ModelSources(
    headerModel: Option[Todo],
    changes:  List[TodoListItemData]
  )

  def updateState(currentTodos: Seq[Todo], sources: ModelSources): Seq[Todo] = {
    val removals: List[Todo] = sources.changes.flatMap(change => change.removal)
    val updates: List[TodoUpdate] = sources.changes.flatMap(change => change.update)

    (currentTodos ++ sources.headerModel).filter(todo => !removals .contains(todo))
      .map{todo => updates.find{update => update.oldTodo == todo} match {
        case Some(foundUpdate) => foundUpdate.newTodo
        case None => todo
      }}
  }

  val sourcesWrapped: Rx[ModelSources] = header.model |@| todoListDataChanges map {
    case(newTodoMaybe: Option[Todo], changes: List[TodoListItemData]) =>
      ModelSources(newTodoMaybe, changes)
    case whatsit  =>
      println("mismatch: " + whatsit.getClass.getName)
      throw new UndefinedBehaviorError("Expected (Option[Todo], List[TodoListItemData])")
  }

  lazy val allTodos: Rx[Seq[Todo]] = sourcesWrapped.flatMap(mSources =>
    allTodos.foldp(load()){(last, next) => updateState(last ++ next, mSources)}
  )


  def focusInput() = dom.document.getElementById("editInput") match {
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
  }
}
