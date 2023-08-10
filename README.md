# Finagle Postgres
[![Continuous Integration](https://github.com/deal-engine/finagle-postgres/actions/workflows/ci.yml/badge.svg?branch=master)](https://github.com/deal-engine/finagle-postgres/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.deal-engine/finagle-postgres_2.13.svg)](https://maven-badges.herokuapp.com/maven-central/io.github.deal-engine/finagle-postgres_2.13)

This library provides [PostgreSQL][postgres] database support for
[Finagle][finagle].

[postgres]: https://www.postgresql.org/
[finagle]: https://github.com/twitter/finagle
[mairbek]: https://github.com/mairbek/finagle-postgres
[thefactory]: https://github.com/thefactory/finagle-postgres
[vkostyukov]: https://github.com/vkostyukov/finagle-postgres

## Documentation
See [the GitHub Pages](https://deal-engine.github.io/finagle-postgres)

## Using the Postgres client

### Installation

Finagle Postgres is published on Maven Central. Use the following _sbt_ snippet to bring it as a
dependency.

```scala
libraryDependencies ++= Seq(
  "io.github.deal-engine" %% "finagle-postgres" % "<release>"
)
```

### Connecting to the DB

```scala
val client = Postgres.Client()
  .withCredentials("user", Some("password"))
  .database("dbname")
  .withSessionPool.maxSize(1) //optional; default is unbounded
  .withBinaryResults(true)
  .withBinaryParams(true)
  .withTransport.tls("host")
  .newRichClient("host:port")
```

### Selecting with simple query

```scala
val f = client.select("select * from users") {row =>
    User(row.getString("email"), row.getString("name"))
}
logger.debug("Responded " + Await.result(f))
```

### Integration Tests

`sbt test` won't run integration tests by default. In order to make it easier for development, a database environment is provided via Docker.
After installing [Docker](https://docs.docker.com/v17.09/engine/installation/) and [docker compose](https://docs.docker.com/compose/install/), run:

```bash
docker-compose up -d
```

A few environment variables need to be provided as well:

```bash
export PG_HOST_PORT=localhost:5432 PG_DBNAME=finagle_postgres_test PG_USER=postgres
```

With the database and variables in place, all tests will be executed. More details in [IntegrationSpec](https://github.com/finagle/finagle-postgres/blob/master/src/test/scala/com/twitter/finagle/postgres/integration/IntegrationSpec.scala#L19-L32)

## Changelog

See [CHANGELOG.md](CHANGELOG.md)

## Contributors
* [Iván Molina Rebolledo](https://github.com/ivanmoreau) (current maintainer)

### Past
* [Mairbek Khadikov][mairbek] (project creator)
* [The Factory][thefactory]
* [Vladimir Kostyukov][vkostyukov]
* [Jeremy Smith](https://github.com/jeremyrsmith)
* [Leon Maia](https://github.com/leonmaia)

## License

Licensed under the **[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0)** (the "License");
you may not use this software except in compliance with the License.

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
