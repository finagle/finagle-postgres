import sbt._
import Keys._


object FinaglePostgres extends Build {

  val baseSettings = Defaults.defaultSettings ++ Seq(resolvers += "twitter-repo" at "http://maven.twttr.com",
    libraryDependencies ++= Seq(
      "com.twitter" % "finagle-core" % "5.3.0",
      
      "com.twitter" % "util-logging" % "5.3.6"
    ))

  lazy val buildSettings = Seq(organization := "Mairbek Khadikov",
    version := "0.0.1-SNAPSHOT",
    scalaVersion := "2.9.2"
  )

  lazy val root = Project(id = "finagle-postgres",
    base = file("."),
    settings = baseSettings ++ buildSettings)

}
