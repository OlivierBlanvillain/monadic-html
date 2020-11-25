import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

inThisBuild(List(
  crossScalaVersions := Seq("2.13.3", "2.12.12"),
  scalaVersion := crossScalaVersions.value.head,
  scalacOptions := Seq(
    "-deprecation",
    "-encoding", "UTF-8",
    "-feature",
    "-unchecked",
    // "-Xfatal-warnings", see Cancelable#cancel
    "-Xlint",
    "-Ywarn-numeric-widen",
    "-Ywarn-value-discard"),
  organization := "in.nvilla",
  scalaJSLinkerConfig ~= { _.withSourceMap(true) },
  licenses := Seq(("MIT", url("http://opensource.org/licenses/MIT"))),
  homepage := Some(url("https://github.com/OlivierBlanvillain/monadic-html")),
  developers := List(
    Developer(
    "OlivierBlanvillain",
    "Olivier Blanvillain",
    "noreply@github.com",
    url("https://github.com/OlivierBlanvillain/")
  )
)))

val scalajsdom = "1.1.0"
val scalatest  = "3.2.2"
val cats       = "2.2.0"

skip in publish := true

lazy val `monadic-html` = project
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(
    `monadic-rxJS`,
    `monadic-rx-catsJS` % "test->compile")
  .settings(
    testSettings,
    libraryDependencies += "org.scala-js"  %%% "scalajs-dom" % scalajsdom,
    libraryDependencies += "org.scalatest" %%% "scalatest" % scalatest % Test)

lazy val `monadic-rxJS`  = `monadic-rx`.js
lazy val `monadic-rxJVM` = `monadic-rx`.jvm
lazy val `monadic-rx`    = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .jvmSettings(skip in publish := true)
  .jsSettings(testSettings)
  .settings(
    libraryDependencies += "org.scalatest" %%% "scalatest" % scalatest % Test)

lazy val `monadic-rx-catsJS`  = `monadic-rx-cats`.js
lazy val `monadic-rx-catsJVM` = `monadic-rx-cats`.jvm
lazy val `monadic-rx-cats`    = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .jvmSettings(skip in publish := true)
  .dependsOn(`monadic-rx`)
  .settings(
    testSettings,
    libraryDependencies += "org.typelevel" %%% "cats-core" % cats)

lazy val `examples` = project
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(`monadic-html`, `monadic-rx-catsJS`)
  .settings(
    testSettings,
    skip in publish := true,
    test in Test := {},
    libraryDependencies += "com.github.japgolly.scalacss" %%% "core" % "0.6.1",
    artifactPath in (Compile, fastLinkJS) :=
      ((crossTarget in (Compile, fastLinkJS)).value /
        ((moduleName in fastLinkJS).value + "-opt.js")))


lazy val testSettings = Seq(
  testOptions  in Test += Tests.Argument(TestFrameworks.ScalaTest, "-oDF"),
  scalaJSStage in Test := FastOptStage,
  jsEnv        in Test := new org.scalajs.jsenv.jsdomnodejs.JSDOMNodeJSEnv())
