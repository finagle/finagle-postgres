// https://typelevel.org/sbt-typelevel/faq.html#what-is-a-base-version-anyway
ThisBuild / tlBaseVersion := "0.15" // your current series x.y

ThisBuild / organization := "io.github.deal-engine"
ThisBuild / organizationName := "Deal Engine"
ThisBuild / startYear := Some(2023)
ThisBuild / licenses := Seq(License.Apache2)
ThisBuild / developers := List(
  // your GitHub handle and name
  tlGitHubDev("ivanmoreau", "Iván Molina Rebolledo"),
  tlGitHubDev("IvanAtDealEngine", "Iván Molina Rebolledo")
)

// publish to s01.oss.sonatype.org (set to true to publish to oss.sonatype.org instead)
ThisBuild / tlSonatypeUseLegacyHost := false

// publish website from this branch
ThisBuild / tlSitePublishBranch := Some("master")

ThisBuild / tlFatalWarnings := false
ThisBuild / tlCiHeaderCheck := false
ThisBuild / githubWorkflowBuildPreamble ++= Seq(
  WorkflowStep.Run(
    commands = List("docker-compose up -d"),
    name = Some("Start up Postgres")
  ),
  WorkflowStep.Run(
    commands = List("./.github/wait-for-postgres.sh"),
    name = Some("Wait for Postgres to be ready")
  )
)
ThisBuild / githubWorkflowEnv ++= Map(
  "PG_HOST_PORT" -> "localhost:5432",
  "PG_USER" -> "postgres",
  "PG_DBNAME" -> "finagle_postgres_test",
  "PG_PASSWORD" -> "test"
)

val Scala213 = "2.13.10"
ThisBuild / crossScalaVersions := Seq(Scala213)
ThisBuild / scalaVersion := Scala213 // the default Scala

val Versions = {
  class Versions {
    val twitter = "22.12.0"
    val circeTesting = "0.14.5"
    val scalaTest = "3.2.16"
    val scalaTestCheck = "3.2.16.0"
    val scalacheck = "1.17.0"
    val scalamock = "5.2.0"
    val quill = "4.6.1"
    val weaver = "0.8.3"
  }
  new Versions
}

lazy val root =
  tlCrossRootProject.aggregate(
    `finagle-postgres`,
    `finagle-postgres-shapeless`,
    `finagle-postgres-quill`,
    `weaver-twitter-future`,
    `weaver-twitter-future-core`
  )

lazy val `finagle-postgres` = crossProject(JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("finagle-postgres"))
  .settings(
    name := "finagle-postgres",
    scalacOptions ++= Seq(
      "-language:implicitConversions"
    ),
    libraryDependencies ++= Seq(
      "com.twitter" %% "finagle-core" % Versions.twitter,
      "com.twitter" %% "finagle-netty4" % Versions.twitter,
      "org.scalatest" %% "scalatest" % Versions.scalaTest % Test,
      "org.scalatestplus" %% "scalacheck-1-17" % Versions.scalaTestCheck % Test,
      "org.scalacheck" %% "scalacheck" % Versions.scalacheck % Test,
      "org.scalamock" %% "scalamock" % Versions.scalamock % Test,
      "io.circe" %% "circe-testing" % Versions.circeTesting % Test
    )
  )

lazy val `finagle-postgres-quill` = crossProject(JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("quill-finagle-postgres"))
  .settings(
    name := "finagle-postgres-quill",
    tlVersionIntroduced := Map("2.13" -> "0.14.1"),
    libraryDependencies ++= Seq(
      "io.getquill" %% "quill-sql" % Versions.quill,
      "org.scalatest" %% "scalatest" % Versions.scalaTest % Test,
      "org.scalatestplus" %% "scalacheck-1-17" % Versions.scalaTestCheck % Test,
      "org.scalacheck" %% "scalacheck" % Versions.scalacheck % Test
    )
  )
  .dependsOn(`finagle-postgres`, `weaver-twitter-future` % "compile->test;")

lazy val `finagle-postgres-shapeless` = crossProject(JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("finagle-postgres-shapeless"))
  .settings(
    name := "finagle-postgres-shapeless",
    libraryDependencies ++= Seq(
      "com.chuusai" %% "shapeless" % "2.3.10",
      "io.github.jeremyrsmith" %% "patchless-core" % "1.0.7",
      "com.twitter" %% "finagle-core" % Versions.twitter,
      "com.twitter" %% "finagle-netty4" % Versions.twitter,
      "org.scalatest" %% "scalatest" % Versions.scalaTest % Test,
      "org.scalatestplus" %% "scalacheck-1-17" % Versions.scalaTestCheck % Test,
      "org.scalacheck" %% "scalacheck" % Versions.scalacheck % Test,
      "org.scalamock" %% "scalamock" % Versions.scalamock % Test,
      "io.circe" %% "circe-testing" % Versions.circeTesting % Test
    )
  )
  .dependsOn(`finagle-postgres`)

lazy val `weaver-twitter-future-core` = crossProject(JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("weaver-twitter-future-core"))
  .settings(
    name := "weaver-twitter-future-core",
    libraryDependencies ++= Seq(
      "com.twitter" %% "util-core" % Versions.twitter,
      "com.disneystreaming" %% "weaver-core" % Versions.weaver,
      "com.disneystreaming" %% "weaver-cats" % Versions.weaver,
      "org.typelevel" %% "catbird-util" % Versions.twitter
    )
  )

lazy val `weaver-twitter-future` = crossProject(JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("weaver-twitter-future"))
  .settings(
    name := "weaver-twitter-future",
    testFrameworks := Seq(new TestFramework("weaver.framework.TwitterFuture")),
    Test / fork := true,
    libraryDependencies ++= Seq(
      "com.disneystreaming" %% "weaver-framework" % Versions.weaver,
      "com.twitter" %% "util-core" % Versions.twitter,
      "org.typelevel" %% "catbird-util" % Versions.twitter
    )
  )
  .dependsOn(`weaver-twitter-future-core`)

lazy val docs = project
  .in(file("site"))
  .enablePlugins(TypelevelSitePlugin)
  .settings(
    tlSiteIsTypelevelProject := None,
    tlSiteHelium ~= {
      import laika.helium.config._
      _.site.topNavigationBar(homeLink =
        IconLink.internal(laika.ast.Path.Root / "main.md", HeliumIcon.home)
      )
    }
  )
  .dependsOn(`finagle-postgres`.jvm, `finagle-postgres-shapeless`.jvm)
