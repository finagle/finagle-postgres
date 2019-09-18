import ReleaseTransformations._

lazy val buildSettings = Seq(
  organization := "io.github.finagle",
  scalaVersion := "2.12.8",
  crossScalaVersions := Seq("2.11.12","2.12.8"),
  fork in Test := true
)

val baseSettings = Seq(
  resolvers += Resolver.bintrayRepo("jeremyrsmith", "maven"),
  libraryDependencies ++= Seq(
    "com.twitter" %% "finagle-core" % "19.9.0",
    "com.twitter" %% "finagle-netty4" % "19.9.0",
    "org.scalatest" %% "scalatest" % "3.0.8" % "test,it",
    "org.scalacheck" %% "scalacheck" % "1.14.1" % "test,it",
    "org.scalamock" %% "scalamock-scalatest-support" % "3.6.0" % "test,it",
    "io.circe" %% "circe-testing" % "0.11.1" % "test,it"
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
  pgpSecretRing := file("local.secring.gpg"),
  pgpPublicRing := file("local.pubring.gpg"),
  releasePublishArtifactsAction := PgpKeys.publishSigned.value,
  releaseIgnoreUntrackedFiles := true,
  licenses := Seq("Apache 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
  homepage := Some(url("https://finagle.github.io/finagle-postgres")),
  autoAPIMappings := true,
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/finagle/finagle-postgres"),
      "scm:git:git@github.com:finagle/finagle-postgres.git"
    )
  ),
  releaseVersionBump := sbtrelease.Version.Bump.Minor,
  releaseProcess := {
    Seq[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
      releaseStepCommandAndRemaining("+clean"),
      releaseStepCommandAndRemaining("+test"),
      setReleaseVersion,
      commitReleaseVersion,
      tagRelease,
      releaseStepCommandAndRemaining("+publishSigned"),
      setNextVersion,
      commitNextVersion,
      releaseStepCommand("sonatypeReleaseAll"),
      pushChanges
    )
  },
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

lazy val shapelessRef = LocalProject("finagle-postgres-shapeless")

lazy val `finagle-postgres` = project.in(file("."))
  .settings(moduleName := "finagle-postgres")
  .settings(allSettings)
  .configs(IntegrationTest)
  .aggregate(shapelessRef)

lazy val `finagle-postgres-shapeless` = project
  .settings(moduleName := "finagle-postgres-shapeless")
  .settings(allSettings)
  .settings(libraryDependencies ++= Seq(
    "com.chuusai" %% "shapeless" % "2.3.3",
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

scalacOptions ++= Seq(
  "-deprecation"
)
