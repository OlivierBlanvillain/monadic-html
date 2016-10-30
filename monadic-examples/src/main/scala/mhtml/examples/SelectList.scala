package mhtml.examples

import scala.xml.Node

import mhtml._

object SelectList extends Example {
  def app: Node = {
    // format: off
    val options = Seq[String](
      "Stankovic", "de Mulder", "Naidenoff", "Hosono", "Connolly", "Barber",
      "Bishop", "Levy", "Haas", "Mineff", "Lewy", "Hanna", "Allison", "Saalfeld",
      "Baxter", "Kelly", "McCoy", "Johnson", "Keane", "Williams", "Allison",
      "Fleming", "Penasco y Castellana", "Abelson", "Francatelli", "Hays",
      "Ryerson", "Lahtinen", "Hendekovic", "Hart", "Nilsson", "Kantor", "Moraweck",
      "Wick", "Spedden", "Dennis", "Danoff", "Slayter", "Caldwell", "Sage",
      "Young", "Nysveen", "Ball", "Goldsmith", "Hippach", "McCoy", "Partner",
      "Graham", "Vander Planke", "Frauenthal", "Denkoff", "Pears", "Burns", "Dahl",
      "Blackwell", "Navratil", "Fortune", "Collander", "Sedgwick", "Fox", "Brown",
      "Smith", "Davison", "Coutts", "Dimic", "Odahl", "Williams-Lambert", "Elias",
      "Arnold-Franchi", "Yousif", "Vanden Steen", "Bowerman", "Funk", "McGovern",
      "Braund", "Karlsson", "Hirvonen", "Goodwin", "Frost", "Rouse", "Turkula",
      "Bishop", "Lefebre", "Hoyt", "Kent", "Somerton", "Coutts", "Hagland",
      "Windelov", "Molson", "Artagaveytia", "Stanley", "Yousseff", "Eustis",
      "Shellard", "Allison", "Svensson", "Calic", "Canavan", "O'Sullivan",
      "Laitinen", "Maioni", "Penasco y Castellana", "Quick", "Bradley", "Olsen",
      "Lang", "Daly", "Webber", "McGough", "Rothschild", "Coleff", "Walker",
      "Lemore", "Ryan", "Angle", "Pavlovic", "Perreault", "Vovk", "Lahoud",
      "Hippach", "Kassem", "Farrell", "Ridsdale", "Farthing", "Salonen", "Hocking",
      "Quick", "Toufik", "Elias", "Peter", "Cacic", "Hart", "Butt", "LeRoy",
      "Risien", "Frolicher", "Crosby", "Andersson", "Andersson", "Beane",
      "Douglas", "Nicholson", "Beane", "Padro y Manent", "Goldsmith", "Davies",
      "Thayer", "Sharp", "O'Brien", "Leeni", "Ohman", "Wright", "Duff Gordon",
      "Robbins", "Taussig", "de Messemaeker", "Morrow", "Sivic", "Norman",
      "Simmons", "Meanwell", "Davies", "Stoytcheff", "Palsson", "Doharr",
      "Jonsson", "Harris", "Appleton", "Flynn", "Kelly", "Rush", "Patchett",
      "Garside", "Silvey", "Caram", "Jussila", "Christy", "Thayer", "Downton",
      "Ross", "Paulner", "Taussig", "Jarvis", "Frolicher-Stehli", "Gilinski",
      "Murdlin", "Rintamaki", "Stephenson", "Elsbury", "Bourke", "Chapman"
    )
    // format: on

    val (app, selected) = Chosen.singleSelect(_ => Var(options))
    val message = selected.map {
      case Some(x) => s"You selected: '$x'"
      case _ => "Please select a name from the list"
    }
    <div>
      <p>{message}</p>
      {app}
    </div>
  }
}
