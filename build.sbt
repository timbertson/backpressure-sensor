import ScalaProject._

val weaverVersion = "0.6.3"
val monixVersion = "3.4.0"
val cats2Version = "2.3.0"
val cats2Deps = List(
  "org.typelevel" %% "cats-core" % cats2Version,
  "org.typelevel" %% "cats-effect" % cats2Version,
)
val monixDeps = cats2Deps ++ List(
    "io.monix" %% "monix" % monixVersion,
)
val weaverDeps = List(
  "com.disneystreaming" %% "weaver-cats" % weaverVersion,
  "com.disneystreaming" %% "weaver-monix" % weaverVersion,
)

ThisBuild / versionScheme := Some("semver-spec")

lazy val commonSettings = Seq(
  organization := "net.gfxmonk",
  testFrameworks += new TestFramework("weaver.framework.CatsEffect"),
  testFrameworks += new TestFramework("weaver.framework.Monix"),
  libraryDependencies ++= monixDeps.map(_ % Test) ++ weaverDeps.map(_ % Test),
)

lazy val core = (project in file("core")).settings(
  commonSettings,
  publicProjectSettings,
  name := "backpressure-sensor-core",
)

lazy val testkit = (project in file("testkit")).settings(
  commonSettings,
  publicProjectSettings,
  name := "backpressure-sensor-testkit",
).dependsOn(core)

lazy val statsd = (project in file("statsd")).settings(
  commonSettings,
  publicProjectSettings,
  name := "backpressure-sensor-statsd",
  libraryDependencies ++= Seq(
    "com.datadoghq" % "java-dogstatsd-client" % "4.0.0"
  )
).dependsOn(core)

lazy val monix = (project in file("monix")).settings(
  commonSettings,
  publicProjectSettings,
  name := "backpressure-sensor-monix",
  libraryDependencies ++= List(
    "org.typelevel" %% "cats-core" % cats2Version,
    "org.typelevel" %% "cats-effect" % cats2Version,
    "io.monix" %% "monix" % monixVersion,
  )
).dependsOn(core, statsd, testkit % "test")

lazy val fs2Cats2 = (project in file("fs2-cats2")).settings(
  commonSettings,
  publicProjectSettings,
  name := "backpressure-sensor-fs2-cats2",
  libraryDependencies ++= cats2Deps ++ List(
    "co.fs2" %% "fs2-core" % "2.5.10",
    "org.typelevel" %% "cats-effect-laws" % cats2Version % "test", // provides TestContext
),
).dependsOn(core, statsd, testkit % "test")

//lazy val fs2Cats3 = (project in file("fs2")).settings(
//  commonSettings,
//  publicProjectSettings,
//  name := "backpressure-sensor-fs2-cats2",
//  libraryDependencies ++= cats3Deps ++ List(
//    "co.fs2" %% "fs2-core" % "3.2.9"
//  ),
//).dependsOn(core, statsd)

lazy val akka = (project in file("akka")).settings(
  commonSettings,
  publicProjectSettings,
  name := "backpressure-sensor-akka",
  libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-stream" % "2.6.15",
  ),
).dependsOn(core, statsd, testkit % "test")

lazy val example = (project in file("example")).settings(
  commonSettings,
  hiddenProjectSettings,
  name := "backpressure-sensor-example",
).dependsOn(monix)

lazy val root = (project in file("."))
  .settings(
    name := "root",
    hiddenProjectSettings
  )
  .aggregate(core, statsd, monix, akka, fs2Cats2, example, testkit)
