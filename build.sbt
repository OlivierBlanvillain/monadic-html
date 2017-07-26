val scalajsdom = "0.9.1"
val scalatest  = "3.0.1"
val cats       = "0.9.0"

crossScalaVersions in ThisBuild := Seq("2.12.1", "2.11.8")
scalaVersion       in ThisBuild := crossScalaVersions.value.head

lazy val root = project.in(file("."))
  .aggregate(
    `monadic-html`,
    `monadic-rxJS`,
    `monadic-rxJVM`,
    `monadic-rx-catsJS`,
    `monadic-rx-catsJVM`,
    `examples`
  )
  .settings(noPublishSettings: _*)

lazy val `monadic-html` = project
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(`monadic-rxJS`)
  .settings(publishSettings: _*)
  .settings(testSettings: _*)
  .settings(libraryDependencies += "org.scala-js"  %%% "scalajs-dom" % scalajsdom)
  .settings(libraryDependencies += "org.scalatest" %%% "scalatest" % scalatest % "test")
  .dependsOn(`monadic-rx-catsJS` % "test->compile")

lazy val `monadic-rxJS`  = `monadic-rx`.js
lazy val `monadic-rxJVM` = `monadic-rx`.jvm
lazy val `monadic-rx`    = crossProject
  .crossType(CrossType.Full)
  .settings(publishSettings: _*)
  .jsSettings(testSettings: _*)
  .settings(libraryDependencies += "org.scalatest" %%% "scalatest" % scalatest % "test")

lazy val `monadic-rx-catsJS`  = `monadic-rx-cats`.js
lazy val `monadic-rx-catsJVM` = `monadic-rx-cats`.jvm
lazy val `monadic-rx-cats`    = crossProject
  .crossType(CrossType.Pure)
  .settings(publishSettings: _*)
  .dependsOn(`monadic-rx`)
  .settings(libraryDependencies += "org.typelevel" %%% "cats" % cats)

lazy val `examples` = project
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(`monadic-html`, `monadic-rx-catsJS`)
  .settings(noPublishSettings: _*)
  .settings(
    emitSourceMaps := true,
    artifactPath in (Compile, fastOptJS) := (
      (crossTarget in (Compile, fastOptJS)).value /
        ((moduleName in fastOptJS).value + "-opt.js")
    )
  )

lazy val `example-todomvc` = project
  .enablePlugins(ScalaJSPlugin, ScalaJSBundlerPlugin)
  .dependsOn(`monadic-html`, `monadic-rx-catsJS`)
  .settings(noPublishSettings: _*)
  .settings(
    libraryDependencies += "com.lihaoyi" %%% "upickle" % "0.4.4",
    emitSourceMaps := true,
    requiresDOM in Test := true,
    webpackConfigFile in fastOptJS := Some(baseDirectory.value / "dev.webpack.config.js"),
    npmDependencies in Compile += "todomvc-app-css" -> "2.1.0",
    artifactPath in (Compile, fastOptJS) := (
      (crossTarget in (Compile, fastOptJS)).value /
        ((moduleName in fastOptJS).value + "-opt.js")
    )
  )

scalacOptions in ThisBuild := Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-feature",
  "-unchecked",
  // "-Xfatal-warnings", see Cancelable#cancel
  "-Xfuture",
  "-Xlint",
  "-Yno-adapted-args",
  "-Ywarn-numeric-widen",
  "-Ywarn-unused-import",
  "-Ywarn-value-discard")

organization in ThisBuild := "in.nvilla"

lazy val testSettings = Seq(
  testOptions  in Test += Tests.Argument(TestFrameworks.ScalaTest, "-oDF"),
  scalaJSStage in Test := FastOptStage,
  jsEnv        in Test := PhantomJSEnv().value)

lazy val publishSettings = Seq(
  publishMavenStyle := true,
  publishArtifact in Test := false,
  pomIncludeRepository := Function.const(false),
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
