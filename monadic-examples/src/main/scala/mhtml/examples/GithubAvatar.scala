package mhtml.examples

import scala.scalajs.js
import scala.scalajs.js.JSON
import scala.scalajs.js.timers.SetTimeoutHandle
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.xml.Node

import mhtml._
import org.scalajs.dom.ext.Ajax

@js.native
trait GhUser extends js.Object {
  val avatar_url: String = js.native
}
@js.native
trait GhRepo extends js.Object {
  val clone_url: String = js.native
  val homepage: String = js.native
  val language: String = js.native
  val name: String = js.native
  val stargazers_count: Int = js.native
}

object GhRepo {
  implicit val GhRepoSearchable = Searcheable.instance[GhRepo](_.name)
}

object GithubAvatar extends Example {
  val api = "https://api.github.com"

  def doRequest[T](suffix: String)(f: js.Dynamic => T): Rx[Option[Try[T]]] =
    Utils
      .fromFuture(Ajax.get(s"$api/$suffix"))
      .map(_.map { t =>
        t.withFilter(_.status == 200).map { x =>
          println(x.responseText)
          val json = JSON.parse(x.responseText)
          println("JSON: " + json)
          f(json)
        }
      })

  def getUser(username: String) =
    doRequest(s"users/$username")(_.asInstanceOf[GhUser])
  def getRepos(username: String): Rx[Option[Try[List[GhRepo]]]] =
    doRequest(s"users/$username/repos?sort=updated") { x =>
      x.asInstanceOf[js.Array[GhRepo]].toList
    }

  object debounce {
    var timeoutHandler: js.UndefOr[SetTimeoutHandle] = js.undefined
    def apply[A, B](timeout: Double)(f: A => B): A => Unit = { a =>
      timeoutHandler foreach js.timers.clearTimeout
      timeoutHandler = js.timers.setTimeout(timeout) {
        f(a)
        ()
      }
    }
  }

  def detailedRepo(repo: GhRepo): Node =
    <div>
      <h2>{repo.name}</h2>
      <p>Stars: {repo.stargazers_count}</p>
      <p>Homepage: <a href={repo.homepage}>{repo.homepage}</a></p>
      <p><pre>git clone {repo.clone_url}</pre></p>
    </div>

  def repo(repo: GhRepo): Node =
    <li>{repo.name}: {repo.stargazers_count}</li>

  def repos(username: String): Rx[Node] = {
    getRepos(username).map {
      case None => <div>Loading repos...</div>
      case Some(Success(repos)) =>
        val (searchList, active) =
          Chosen.singleSelect[GhRepo](_ => Var(repos),
                                      placeholder = "Search for repo...")
        <div>
          {searchList}
          {active.map(_.map(detailedRepo).getOrElse(<div></div>))}
        </div>
      case Some(Failure(error)) => <div style="background: red">{error}</div>
    }
  }

  def avatar(username: String): Rx[Node] = getUser(username).map {
    case None => <div>Loading avatar for {username}</div>
    case Some(Success(json)) =>
      <img style="height: 11em" src={json.avatar_url.toString }/>
    case Some(Failure(error)) =>
      <div style="background: red">{error}</div>
  }

  def profile(username: String): Node = {
    if (username == "") <div>Please enter your username</div>
    else {
      <div>
        {avatar(username)}
        {repos(username)}
      </div>
    }
  }

  def app: Node = {
    val rxUsername = Var("")
    val onkeyup =
      Utils.inputEvent(input => rxUsername.update(_ => input.value))
    <div>
      <input type="text" onkeyup={debounce(300)(onkeyup)}/>
      {rxUsername.map(profile)}
    </div>
  }
}
