lazy val supportedScalaVersions = Seq("2.12.21", "2.13.18", "3.8.4")

organization := "dev.kovstas"
scalaVersion := "3.8.4"
scalacOptions ++= scalaOptions(scalaVersion.value)

libraryDependencies ++= Seq(
  "co.fs2" %% "fs2-core" % "3.13.0",
  "org.scalameta" %% "munit" % "1.3.4" % Test,
  "org.typelevel" %% "cats-effect-testkit" % "3.7.0" % Test
)
libraryDependencySchemes ++= Seq("2.12", "2.13", "3").map { binaryVersion =>
  "org.scala-native" % s"test-interface_native0.5_$binaryVersion" % VersionScheme.EarlySemVer
}

lazy val root =
  (projectMatrix in file("."))
    .settings(
      name := "fs2-throttler"
    )
    .enablePlugins(AutomateHeaderPlugin)
    .jvmPlatform(scalaVersions = supportedScalaVersions)
    .jsPlatform(scalaVersions = supportedScalaVersions)
    .nativePlatform(scalaVersions = supportedScalaVersions)

def scalaOptions(v: String) = {
  val options = List(
    "-deprecation",
    "-feature",
    "-unchecked",
    "-language:higherKinds"
  )

  CrossVersion.partialVersion(v) match {
    case Some((3, _)) => ("-Werror" :: options) :+ "-source:3.0-migration"
    case _            => "-Xfatal-warnings" :: options
  }
}

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
versionScheme := Some("early-semver")
scalafmtOnCompile := true
