lazy val buildSettings = Seq(
  organization := "com.twitter",
  version := "0.1.0-SNAPSHOT",
  scalaVersion := "2.11.6",
  crossScalaVersions := Seq("2.10.5", "2.11.6"),
  libraryDependencies ++= Seq(
    "com.twitter" %% "finagle-core" % "6.25.0",
    "junit" % "junit" % "4.7" % "test,it",
    "org.scalatest" %% "scalatest" % "2.2.4" % "test,it",
    "org.mockito" % "mockito-all" % "1.9.5" % "test,it"
  )
)

lazy val root = project.in(file("."))
  .settings(moduleName := "finagle-postgres")
  .settings(buildSettings)
  .configs(IntegrationTest)
