package examples

import scala.xml.Node

import mhtml._

object ContactList extends Example {
  case class Contact(name: Var[String])

  def row(contact: Contact): Node =
    <tr>
      <td>
        {contact.name}
      </td>
      <td>
        <button onclick={() => contact.name.update("Mr. " + _)}>
          click me
        </button>
      </td>
    </tr>
  def app: Node = {
    val contacts = Var(Seq.empty[Contact])
    <section>
      <div>
        <button onclick={() => contacts.update(x => Contact(Var("Olaf")) +: x)}>
          Add a contact
        </button>
      </div>
      <table>
        <thead>
          <tr>
            <th>Name</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {contacts.map(_.map(row))}
        </tbody>
      </table>
    </section>
  }
}
