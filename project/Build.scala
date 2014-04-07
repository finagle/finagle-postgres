import sbt._
import Keys._


object FinaglePostgres extends Build {

  val baseSettings = Defaults.defaultSettings ++ Seq(resolvers += "twitter-repo" at "http://maven.twttr.com",
    libraryDependencies ++= Seq(
      "org.specs2" %% "specs2" % "1.14" % "it,test",
      "junit" % "junit" % "4.7" % "test, it",
      "com.twitter" %% "finagle-core" % "6.10.0"
    ))

  lazy val buildSettings = Seq(
    organization := "com.github.mairbek",
    version := "6.10.0.2-SNAPSHOT",
    scalaVersion := "2.10.2"
  )

  lazy val publishSettings = Seq(
    publishMavenStyle := true,
    publishArtifact := true,
    //publishTo := Some(Resolver.file("localDirectory", file(Path.userHome.absolutePath + "/Projects/personal/mvn-repo"))),
    credentials += Credentials(Path.userHome / ".thefactory" / "credentials"),
    publishTo <<= version { (v: String) =>
      val nexus = "http://maven.thefactory.com/nexus/content/repositories/"
      if (v.trim.endsWith("SNAPSHOT"))
        Some("snapshots" at nexus + "snapshots")
      else
        Some("releases"  at nexus + "releases")
    },
    licenses := Seq("Apache 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    homepage := Some(url("https://github.com/mairbek/finagle-postgres")),
    pomExtra := (
      <scm>
        <url>git://github.com/mairbek/finagle-postgres.git</url>
        <connection>scm:git://github.com/mairbek/finagle-postgres.git</connection>
      </scm>
        <developers>
          <developer>
            <id>mairbek</id>
            <name>Mairbek Khadikov</name>
            <url>http://github.com/mairbek</url>
          </developer>
        </developers>)
  )

  lazy val root = Project(id = "finagle-postgres",
    base = file("."),
    settings = Defaults.itSettings ++ baseSettings ++ buildSettings ++ publishSettings)
      .configs( IntegrationTest)

}
