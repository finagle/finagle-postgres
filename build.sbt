// https://typelevel.org/sbt-typelevel/faq.html#what-is-a-base-version-anyway
ThisBuild / tlBaseVersion := "0.14" // your current series x.y

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
ThisBuild / tlSitePublishBranch := Some("main")
ThisBuild / tlCiHeaderCheck := false

val Scala213 = "2.13.8"
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
  }
  new Versions
}

lazy val root =
  tlCrossRootProject.aggregate(`finagle-postgres`, `finagle-postgres-shapeless`)

lazy val `finagle-postgres` = crossProject(JVMPlatform)
  .crossType(CrossType.Pure)
  .in(file("finagle-postgres"))
  .settings(
    name := "finagle-postgres",
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

lazy val docs = project
  .in(file("site"))
  .enablePlugins(TypelevelSitePlugin)
  .dependsOn(`finagle-postgres`.jvm)
