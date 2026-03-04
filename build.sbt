val scala3Version = "3.6.4"

lazy val root = project
  .in(file("."))
  .settings(
    name := "link-extractor",
    version := "0.1.0",
    scalaVersion := scala3Version,

    libraryDependencies ++= Seq(
      "org.jsoup"      % "jsoup" % "1.18.3",
      "org.scalameta" %% "munit" % "1.1.0" % Test
    ),

    // MUnit test framework registration
    testFrameworks += TestFramework("munit.Framework"),

    // Compiler options: catch common mistakes early
    scalacOptions ++= Seq(
      "-deprecation",
      "-feature",
      "-unchecked"
    )
  )
