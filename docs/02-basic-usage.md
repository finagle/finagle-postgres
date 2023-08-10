---
title: Basic Usage
layout: default
---

# Basic Usage

Finagle-postgres follows the conventions of the rest of the finagle ecosystem. To connect to a PostgreSQL server, use
the client builder accessed by `com.twitter.finagle.Postgres.Client()`:


```scala mdoc:invisible
import com.twitter.util.Await
//object dontrun {
```

```scala mdoc
import com.twitter.finagle.Postgres

val client = Postgres.Client()
  .withCredentials("user", Some("password"))
  .database("dbname")
  .withSessionPool.maxSize(1)
  .withBinaryResults(true)
  .withBinaryParams(true)
  .withTransport.tls("host")
  .newRichClient("localhost:5432")
```

```scala mdoc:invisible

    Await.result(client.close())
//}
```

## Configuration

`Postgres.Client()` is a builder for finagle-postgres' stack-based client; it offers a fluent API for the most common
configuration needs:

##### Specify credentials (required)
Use `withCredentials` to provide a username and password for the connection.
* `withCredentials(user: String, password: Option[String])`
* `withCredentials(user: String, password: String)`
* `withCredentials(user: String)`

##### Specify database name (required)
* `database(name: String)` - provide the name of the database to which finagle-postgres should connect

##### Configure transport
* `withBinaryResults(enable: Boolean)` - enable or disable using binary format for results on the wire (default disabled)
* `withBinaryParams(enable: Boolean)` - enable or disable using binary format for parameters on the wire (default disabled)
* `withTransport.tls(hostname: String)` - enable SSL using the given hostname

##### Configure connection pool
* `.withSessionPool.maxSize(size: Int)` - configure the maximum number of connections in the pool
* `.withSessionPool.minSize(size: Int)` - configure the minimum number of connections in the pool

Additionally, since `Postgres.Client()` is a standard Finagle stack client, it can be configured in myriad other ways.
Take a look at the [Finagle Clients guide](https://twitter.github.io/finagle/guide/Clients.html) for more information.

Since you'll typically will want to interact with Postgres at the level of queries, rather than individual protocol-level
requests and responses, use `.newRichClient(destination: String)` to create a `com.twitter.finagle.postgres.Client`,
which provides the main API for using the PostgreSQL database.

Next, read about [Simple Queries](03-simple-queries.md)

