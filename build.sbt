import ReleaseTransformations._

lazy val buildSettings = Seq(
  organization := "io.github.deal-engine",
  scalaVersion := "2.13.8",
  crossScalaVersions := Seq("2.13.8"),
  fork in Test := true
)



val Versions = {
  class Versions {
    val twitter = "22.12.0"
    val circeTesting = "0.14.5"
    val scalaTest = "3.2.16"
    val scalaTestCheck = "3.2.16.0"
    val scalacheck = "1.17.0"
    val scalamock = "5.2.0"
  }
  new Versions
}

val baseSettings = Seq(
  resolvers += Resolver.bintrayRepo("jeremyrsmith", "maven"),
  libraryDependencies ++= Seq(
    "com.twitter" %% "finagle-core" % Versions.twitter,
    "com.twitter" %% "finagle-netty4" % Versions.twitter,
    "org.scalatest" %% "scalatest" % Versions.scalaTest % "test,it",
    "org.scalatestplus" %% "scalacheck-1-17" % Versions.scalaTestCheck % "test,it",
    "org.scalacheck" %% "scalacheck" % Versions.scalacheck % "test,it",
    "org.scalamock" %% "scalamock" % Versions.scalamock % "test,it",
    "io.circe" %% "circe-testing" % Versions.circeTesting % "test,it"
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
      Some("releases" at nexus + "service/local/staging/deploy/maven2")
  },
  publishArtifact in Test := false,
  pgpSecretRing := file("local.secring.gpg"),
  pgpPublicRing := file("local.pubring.gpg"),
  releasePublishArtifactsAction := PgpKeys.publishSigned.value,
  releaseIgnoreUntrackedFiles := true,
  licenses := Seq(
    "Apache 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")
  ),
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

lazy val `finagle-postgres` = project
  .in(file("."))
  .settings(moduleName := "finagle-postgres")
  .settings(allSettings)
  .configs(IntegrationTest)
  .aggregate(shapelessRef)

lazy val `finagle-postgres-shapeless` = project
  .settings(moduleName := "finagle-postgres-shapeless")
  .settings(allSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.chuusai" %% "shapeless" % "2.3.3",
      "io.github.jeremyrsmith" %% "patchless-core" % "1.0.7"
    )
  )
  .configs(IntegrationTest)
  .dependsOn(`finagle-postgres`)

val scaladocVersionPath = settingKey[String]("Path to this version's ScalaDoc")
val scaladocLatestPath = settingKey[String]("Path to latest ScalaDoc")
val tutPath = settingKey[String]("Path to tutorials")

lazy val docs = project
  .settings(moduleName := "finagle-postgres-docs", buildSettings)
  .enablePlugins(GhpagesPlugin, ScalaUnidocPlugin)
  .settings(
    scaladocVersionPath := ("api/" + version.value),
    scaladocLatestPath := (if (isSnapshot.value) "api/latest-snapshot"
                           else "api/latest"),
    tutPath := "doc",
    includeFilter in makeSite := (includeFilter in makeSite).value || "*.md" || "*.yml",
    addMappingsToSiteDir(
      mappings in (ScalaUnidoc, packageDoc),
      scaladocLatestPath
    ),
    addMappingsToSiteDir(
      mappings in (ScalaUnidoc, packageDoc),
      scaladocVersionPath
    ),
    ghpagesNoJekyll := false,
    git.remoteRepo := "git@github.com:finagle/finagle-postgres"
  )
  .dependsOn(`finagle-postgres`, `finagle-postgres-shapeless`)

parallelExecution in Test := false

scalacOptions ++= Seq(
  "-deprecation"
)
