# Finagle Postgres
[![Build status](https://img.shields.io/travis/finagle/finagle-postgres/master.svg)](http://travis-ci.org/finagle/finagle-postgres)
[![Maven Central](https://img.shields.io/maven-central/v/com.github.finagle/finagle-postgres_2.11.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.finagle/finagle-postgres_2.11)
[![Join the chat at https://gitter.im/finagle/finagle-postgres](https://badges.gitter.im/finagle/finagle-postgres.svg)](https://gitter.im/finagle/finagle-postgres?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)


This library provides [PostgreSQL][postgres] database support for
[Finagle][finagle]. It was originally developed by [Mairbek Khadikov][mairbek],
with subsequent work by [The Factory][thefactory],
[Vladimir Kostyukov][vkostyukov], and others. In early 2015 Twitter began using
the library, and this repository reflects the most recent changes (as of March
2015) from The Factory's fork together with changes from Twitter's internal fork
(including new [SSL support][ssl-support]).

The library is currently cross-built for Scala 2.10 and 2.11 using [SBT][sbt].

[postgres]: https://www.postgresql.org/
[finagle]: https://github.com/twitter/finagle
[mairbek]: https://github.com/mairbek/finagle-postgres
[thefactory]: https://github.com/thefactory/finagle-postgres
[vkostyukov]: https://github.com/vkostyukov/finagle-postgres
[ssl-support]: https://github.com/finagle/finagle-postgres/commit/88b45475736a3ba59e76ef8db4e0a633a220e34e
[sbt]: http://www.scala-sbt.org/

## Using the Postgres client

### Installation

Finagle Postgres is published on Maven Central. Use the following _sbt_ snippet to bring it as a
dependency.

* for the _stable_ release:

```scala
libraryDependencies ++= Seq(
  "com.github.finagle" %% "finagle-postgres" % "0.1.0"
)
```

* for the `SNAPSHOT` version:

```scala
resolvers += Resolver.sonatypeRepo("snapshots")

libraryDependencies ++= Seq(
  "com.github.finagle" %% "finagle-postgres" % "0.2.0-SNAPSHOT" changing()
)
```

### Connecting to the DB

```scala
import com.twitter.finagle.postgres.Client
val client = Client(host, username, password, database)
```

### Selecting with simple query

```scala
val f = client.select("select * from users") {row =>
    User(row.getString("email"), row.getString("name"))
}
logger.debug("Responded " + f.get)
```

## Changelog

### 0.1.0
* SSL support
* Manual transactions support

### 0.0.2
* Prepared statements support
* Async responses logging
* Exceptions handling
* Create and drop table support

### 0.0.1
* Clear-text authentication support
* MD5 authentication support
* Select, update, insert, and delete queries

## License

Licensed under the **[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0)** (the "License");
you may not use this software except in compliance with the License.

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
