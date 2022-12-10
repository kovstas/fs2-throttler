name := "fs2-throttler"
organization := "dev.kovstas"

scalaVersion := "2.13.10"
crossScalaVersions := List("2.12.16", "2.13.10", "3.1.2")
scalacOptions ++= scalaOptions(scalaVersion.value)

libraryDependencies ++= Seq(
  "co.fs2" %% "fs2-core" % "3.2.7",
  "org.scalameta" %% "munit" % "0.7.29" % Test,
  "org.typelevel" %% "cats-effect-testkit" % "3.3.11" % Test
)

def scalaOptions(v: String) = {
  val options = List(
    "-Xfatal-warnings",
    "-deprecation",
    "-feature",
    "-unchecked",
    "-language:higherKinds"
  )

  CrossVersion.partialVersion(v) match {
    case Some((3, _)) => options :+ "-source:3.0-migration"
    case _            => options
  }
}
enablePlugins(AutomateHeaderPlugin)

startYear := Some(2021)
organizationName := "Stanislav Kovalenko"

homepage := Some(url("https://github.com/kovstas/fs2-throttler"))
licenses := List("MIT" -> url("http://opensource.org/licenses/MIT"))
scmInfo := Some(
  ScmInfo(
    url("https://github.com/kovstas/fs2-throttler"),
    "scm:git@github.com:kovstas/fs2-throttler.git"
  )
)
developers := List(
  Developer(
    id = "kovstas",
    name = "Stanislav Kovalenko",
    email = "mail@kovstas.dev",
    url = url("https://kovstas.dev")
  )
)
description := "Throttling for FS2 based on the Token bucket algorithm"
sonatypeCredentialHost := "s01.oss.sonatype.org"
versionScheme := Some("early-semver")
scalafmtOnCompile := true
