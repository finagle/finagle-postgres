lazy val buildSettings = Seq(
  organization := "com.github.finagle",
  version := "0.1.0",
  scalaVersion := "2.11.7",
  crossScalaVersions := Seq("2.10.5", "2.11.7")
)

val baseSettings = Seq(
  libraryDependencies ++= Seq(
    "com.twitter" %% "finagle-core" % "6.35.0",
    "junit" % "junit" % "4.7" % "test,it",
    "org.scalatest" %% "scalatest" % "2.2.5" % "test,it",
    "org.mockito" % "mockito-all" % "1.9.5" % "test,it"
  )
)

lazy val publishSettings = Seq(
  publishMavenStyle := true,
  publishArtifact := true,
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value)
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases"  at nexus + "service/local/staging/deploy/maven2")
  },
  publishArtifact in Test := false,
  licenses := Seq("Apache 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
  homepage := Some(url("https://github.com/finagle/finagle-oauth2")),
  autoAPIMappings := true,
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/finagle/finagle-postgres"),
      "scm:git:git@github.com:finagle/finagle-postgres.git"
    )
  ),
  pomExtra :=
    <developers>
      <developer>
        <id>finagle</id>
        <name>Finagle OSS</name>
        <url>https://twitter.com/finagle</url>
      </developer>
    </developers>
)

lazy val allSettings = baseSettings ++ buildSettings ++ publishSettings

lazy val root = project.in(file("."))
  .settings(moduleName := "finagle-postgres")
  .settings(allSettings)
  .configs(IntegrationTest)

parallelExecution in Test := false
