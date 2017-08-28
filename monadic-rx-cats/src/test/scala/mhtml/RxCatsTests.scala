package mhtml

import cats.implicits._
import mhtml.implicits.cats._
import org.scalatest.FunSuite

class RxCatsTests extends FunSuite {

  test("cyclic_update: simple list") {
    val value: Var[Int] = Var(0)
    val valueList: Var[List[Int]] = Var(Nil)
    val valuesRunner = value |@| valueList map {
      case (newInt, oldList) =>
        val newList = List(newInt)
        println(s"oldcomps = $oldList;\nnewcomps= $newList\n${oldList == newList}") // DEBUG
        if (oldList != newList) {
          valueList := newList
        }
    }
    val cc = valuesRunner.impure.foreach(x => ())
    valueList.map(list => assert(list == Nil))
    value := 3
    valueList.map(list => assert(list.head == 3))
    value := 2
    valueList.map(list => assert(list.head == 2))
    value := 5
    valueList.map(list => assert(list.head == 5))

    cc.cancel
    assert(value.isHot)
  }

  test("cyclic_update: component updater") {

    // Dummy node class
    case class Node(data: Any)

    sealed abstract class AbstractComponent[D](view: Node, model: Rx[D])
    case class Component[D](view: Node, model: Rx[D]) extends AbstractComponent[D](view, model)
    case class TaggedComponent[D,T](view: Node, model: Rx[D], tag: T) extends AbstractComponent[D](view, model)

    case class Todo(title: String, completed: Boolean)
    case class TodoList(text: String, hash: String, items: Rx[List[Todo]])

    sealed trait TodoEvent
    final case class UpdateEvent(oldTodo: Todo, newTodo: Todo) extends TodoEvent
    final case class AddEvent(newTodo: Todo) extends TodoEvent
    final case class RemovalEvent(todo: Todo) extends TodoEvent

    val todoList1: Var[List[Todo]] = Var(Nil)
    val todoList2: Var[List[Todo]] = Var(List(Todo("asdf", false)))
    val todoList3: Var[List[Todo]] = Var(List(Todo("asdf", true), Todo("1234", false)))


    val all       = TodoList("All", "#/", todoList1)
    val active    = TodoList("Active", "#/active", todoList2)
    val completed = TodoList("Completed", "#/completed",todoList3)

    val currentTodoList: Var[TodoList] = Var(all)

    def makeFix[T](r: Var[T]): (Rx[T] => Rx[T]) => Rx[T] = {
      val fix: (Rx[T] => Rx[T]) => Rx[T] = {f =>
        f(r).map { v => r := v; v }
      }
      fix
    }

    var todoListItemCounter: Int = 0
    def todoListItem(todo: Todo): TaggedComponent[Option[TodoEvent], Todo] = {
      val someEvent = Rx(Some(AddEvent(todo)))
      todoListItemCounter += 1
      TaggedComponent(Node(todoListItemCounter), someEvent, todo)
    }

    val todoListComponentsFix = makeFix(
      Var[List[TaggedComponent[Option[TodoEvent], Todo]]](Nil)
    )

    val todoListComponents = todoListComponentsFix { tlc =>
      (currentTodoList |@| tlc map {
        case (currentTodos: TodoList, currentComps: List[TaggedComponent[Option[TodoEvent], Todo]]) =>
          println(s"oldcomps in first var = $currentComps")
          currentTodos.items.map(_.map{todo =>
            val idx = currentComps.indexWhere{cmp => cmp.tag == todo}
            println(s"comp index is $idx") // DEBUG
            if (idx >= 0) currentComps(idx)
            else todoListItem(todo)
          })
      }).flatMap(x => x)
    }

    currentTodoList := active
    currentTodoList := all
    todoListComponents.map(list => assert(list != Nil))

  }


}
