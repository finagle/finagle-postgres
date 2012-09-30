finagle-postgres
================

Postgres database support for finagle.


##### Working with postgres client

	val client = Client(host, username, password, database)

	val f = client.select("select * from users") {row =>
      User(row.getString("email"), row.getString("name"))
    }

    logger.debug("Responded " + f.get)


##### Adding dependencies

Maven

  <repositories>
    <repository>
       <id>twitterrepo</id>
       <name>twitter-repo</name>
       <url>http://maven.twttr.com/</url>
       <layout>default</layout>
     </repository>
  </repositories>

  <dependency>
    <groupId>com.github.mairbek</groupId>
    <artifactId>finagle-postgres_2.9.2</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <scope>compile</scope>
  </dependency>

sbt
  
  "com.github.mairbek" % "finagle-postgres" % "0.0.1-SNAPSHOT"