import ReleaseTransformations._

lazy val buildSettings = Seq(
  organization := "io.github.finagle",
  scalaVersion := "2.12.10",
  crossScalaVersions := Seq("2.11.12", "2.12.10", "2.13.1"),
  fork in Test := true
)

def circeTestingVersion(scalaV: String) = {
  if (scalaV.startsWith("2.11")) "0.11.2" else "0.12.3"
}

val baseSettings = Seq(
  resolvers += Resolver.bintrayRepo("jeremyrsmith", "maven"),
  libraryDependencies ++= Seq(
    "com.twitter" %% "finagle-core" % "19.12.0",
    "com.twitter" %% "finagle-netty4" % "19.12.0",
    "org.scalatest" %% "scalatest" % "3.0.8" % "test,it",
    "org.scalacheck" %% "scalacheck" % "1.14.3" % "test,it",
    "org.scalamock" %% "scalamock" % "4.4.0" % "test,it",
    "io.circe" %% "circe-testing" % circeTestingVersion(scalaVersion.value) % "test,it"
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
lazy val integrationTestRef = LocalProject("finagle-postgres-integration")

lazy val `finagle-postgres` = project
  .in(file("."))
  .settings(moduleName := "finagle-postgres")
  .settings(allSettings)
  .configs(IntegrationTest)
  .aggregate(
    shapelessRef,
    integrationTestRef
  )

lazy val `finagle-postgres-integration` = project
  .settings(moduleName := "finagle-postgres-integration")
  .settings(allSettings)
  .configs(IntegrationTest)
  .settings(
    libraryDependencies ++= Seq(
      "io.zonky.test" % "embedded-postgres" % "1.2.6" % "test"
    )
  )
  .dependsOn(`finagle-postgres` % "test->test")
  .aggregate(
    test9,
    test10,
    test11
  )

lazy val test9 = integrationTests("9.6.17")
lazy val test10 = integrationTests("10.11.0") // 10.12.0 fails to find libz for some reason
lazy val test11 = integrationTests("11.6.0")

def integrationTests(v: String) = {
  val majorVersion = v.split('.') match {
    case Array(major, _, _) => major
    case _ => sys.error(s"unexpected version number. Expected major.minor.patch, got $v")
  }
  val id = s"finagle-postgres-test-$majorVersion"
  Project(id = id, base = file(id))
    .settings(baseSettings ++ buildSettings) // don't publish
    .settings(
      libraryDependencies ++= Seq(
        // TODO
        "io.zonky.test.postgres" % "embedded-postgres-binaries-darwin-amd64" % v % "test",
      )
    )
    .settings(
      parallelExecution in Test := false,
      javaOptions in Test += "-Duser.timezone=UTC" // TODO: investigate and submit a test to demonstrate that timezone handling is broken.
    )
    .settings(
      Test / sourceGenerators += Def.task {
        val file = (Test / sourceManaged).value / "com" / "twitter" / "finagle" / "postgres" / "IntegrationSpec.scala"
        IO.write(file,
          s"""package com.twitter.finagle.postgres
            |
            |class IntegrationSpec extends BaseIntegrationSpec("$v")
            |""".stripMargin)
        Seq(file)
      }.taskValue
    )
    .configs(IntegrationTest)
    .dependsOn(integrationTestRef % "compile->compile;test->test")
}

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
  .enablePlugins(GhpagesPlugin, TutPlugin, ScalaUnidocPlugin)
  .settings(
    scaladocVersionPath := ("api/" + version.value),
    scaladocLatestPath := (if (isSnapshot.value) "api/latest-snapshot"
                           else "api/latest"),
    tutPath := "doc",
    includeFilter in makeSite := (includeFilter in makeSite).value || "*.md" || "*.yml",
    addMappingsToSiteDir(tut in Compile, tutPath),
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

javaOptions in Test += "-Duser.timezone=UTC"

scalacOptions ++= Seq(
  "-deprecation"
)
