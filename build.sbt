
lazy val buildSettings = Seq(
  organization := "io.github.finagle",
  version := "0.6.0",
  scalaVersion := "2.12.4",
  crossScalaVersions := Seq("2.11.12","2.12.4"),
  fork in Test := true
)

val baseSettings = Seq(
  resolvers += Resolver.bintrayRepo("jeremyrsmith", "maven"),
  libraryDependencies ++= Seq(
    "com.twitter" %% "finagle-core" % "18.1.0",
    "com.twitter" %% "finagle-netty3" % "18.1.0",
    "org.scalatest" %% "scalatest" % "3.0.4" % "test,it",
    "org.scalacheck" %% "scalacheck" % "1.13.5" % "test,it",
    "org.scalamock" %% "scalamock-scalatest-support" % "3.4.2" % "test,it"
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
  homepage := Some(url("https://finagle.github.io/finagle-postgres")),
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

lazy val `finagle-postgres` = project.in(file("."))
  .settings(moduleName := "finagle-postgres")
  .settings(allSettings)
  .configs(IntegrationTest)

lazy val `finagle-postgres-shapeless` = project
  .settings(moduleName := "finagle-postgres-shapeless")
  .settings(allSettings)
  .settings(libraryDependencies ++= Seq(
    "com.chuusai" %% "shapeless" % "2.3.2",
    "io.github.jeremyrsmith" %% "patchless" % "1.0.4"
  ))
  .configs(IntegrationTest)
  .dependsOn(`finagle-postgres`)

val scaladocVersionPath = settingKey[String]("Path to this version's ScalaDoc")
val scaladocLatestPath = settingKey[String]("Path to latest ScalaDoc")
val tutPath = settingKey[String]("Path to tutorials")

lazy val docs = project
  .settings(moduleName := "finagle-postgres-docs", buildSettings)
  .enablePlugins(GhpagesPlugin, TutPlugin, ScalaUnidocPlugin)
  .settings(
    scaladocVersionPath := ("api/" + version.value),
    scaladocLatestPath := (if (isSnapshot.value) "api/latest-snapshot" else "api/latest"),
    tutPath := "doc",
    includeFilter in makeSite := (includeFilter in makeSite).value || "*.md" || "*.yml",
    addMappingsToSiteDir(tut in Compile, tutPath),
    addMappingsToSiteDir(mappings in(ScalaUnidoc, packageDoc), scaladocLatestPath),
    addMappingsToSiteDir(mappings in(ScalaUnidoc, packageDoc), scaladocVersionPath),
    ghpagesNoJekyll := false,
    git.remoteRepo := "git@github.com:finagle/finagle-postgres"

  ).dependsOn(`finagle-postgres`, `finagle-postgres-shapeless`)



parallelExecution in Test := false

javaOptions in Test += "-Duser.timezone=UTC"

test in Test in `finagle-postgres` := {
  (test in Test in `finagle-postgres`).value
  (test in Test in `finagle-postgres-shapeless`).value
}

scalacOptions ++= Seq(
  "-deprecation"
)
