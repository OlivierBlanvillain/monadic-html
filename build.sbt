val scalajsdom = "0.9.1"
val scalaxml   = "1.0.6"
val scalatest  = "3.0.0"
val cats       = "0.7.2"

scalaVersion  in ThisBuild := "2.11.8"

lazy val root = project.in(file("."))
  .aggregate(`monadic-html`, `monadic-rxJS`, `monadic-rxJVM`, `monadic-rx-catsJS`, `monadic-rx-catsJVM`, tests)
  .settings(noPublishSettings: _*)

lazy val `monadic-html` = project
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(`monadic-rxJS`)
  .settings(publishSettings: _*)
  .settings(libraryDependencies ++= Seq(
    "org.scala-js" %%% "scalajs-dom" % scalajsdom,
    "in.nvilla"    %%% "scala-xml"   % scalaxml)) // Awaiting https://github.com/scala/scala-xml/pull/109

lazy val `monadic-rxJS`  = `monadic-rx`.js
lazy val `monadic-rxJVM` = `monadic-rx`.jvm
lazy val `monadic-rx`    = crossProject
  .crossType(CrossType.Full)
  .settings(publishSettings: _*)

lazy val `monadic-rx-catsJS`  = `monadic-rx-cats`.js
lazy val `monadic-rx-catsJVM` = `monadic-rx-cats`.jvm
lazy val `monadic-rx-cats`    = crossProject
  .crossType(CrossType.Pure)
  .settings(publishSettings: _*)
  .dependsOn(`monadic-rx`)
  .settings(libraryDependencies += "org.typelevel" %%% "cats" % cats)

lazy val tests = project
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(`monadic-html`)
  .settings(noPublishSettings: _*)
  .settings(
    libraryDependencies += "org.scalatest" %%% "scalatest" % scalatest % "test",
    testOptions  in Test += Tests.Argument(TestFrameworks.ScalaTest, "-oDF"),
    scalaJSStage in Test := FastOptStage,
    jsEnv        in Test := PhantomJSEnv().value)

scalacOptions in ThisBuild := Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-feature",
  "-unchecked",
  "-Xfatal-warnings",
  "-Xfuture",
  "-Xlint",
  "-Yinline-warnings",
  "-Yno-adapted-args",
  "-Ywarn-dead-code",
  "-Ywarn-numeric-widen",
  "-Ywarn-unused-import",
  "-Ywarn-value-discard")

lazy val publishSettings = Seq(
  releasePublishArtifactsAction := PgpKeys.publishSigned.value,
  releaseProcess += ReleaseStep(action = Command.process("sonatypeReleaseAll", _)),
  publishArtifact in Test := false,
  pomIncludeRepository := Function.const(false),
  organization := "in.nvilla",
  licenses := Seq(("MIT", url("http://opensource.org/licenses/MIT"))),
  homepage := Some(url("https://github.com/OlivierBlanvillain/monadic-html")),
  publishMavenStyle := true,
  publishTo := {
    if (isSnapshot.value) Some("snapshots" at "https://oss.sonatype.org/content/repositories/snapshots")
    else                  Some("releases"  at "https://oss.sonatype.org/service/local/staging/deploy/maven2")
  },
  pomExtra := {
    <scm>
      <url>git@github.com:OlivierBlanvillain/monadic-html.git</url>
      <connection>scm:git:git@github.com:OlivierBlanvillain/monadic-html.git</connection>
    </scm>
    <developers>
      <developer>
        <id>OlivierBlanvillain</id>
        <name>Olivier Blanvillain</name>
        <url>https://github.com/OlivierBlanvillain/</url>
      </developer>
    </developers>
  }
)

lazy val noPublishSettings = Seq(
  publish := (),
  publishLocal := (),
  publishArtifact := false
)
