---
title: Simple Queries
layout: default
---

# Simple queries

Finagle-postgres can use PostgreSQL's simple query interface to perform queries. These queries are limited, because they
cannot be parameterized - any data that will be provided by the client must be included in the query string itself.
Therefore, the simple query interface should not be used when any user input is going to be used in the query, as that
could be a vector for SQL injection attacks.

Still, it can be useful to try them out.

```tut:invisible
import com.twitter.finagle.Postgres
import com.twitter.util.Await
// create the client based on environment variables
val client = {
  Postgres.Client()
    .withCredentials(sys.env("PG_USER"), sys.env.get("PG_PASSWORD"))
    .database(sys.env("PG_DBNAME"))
    .withSessionPool.maxSize(1)
    .withBinaryResults(true)
    .withBinaryParams(true)
    .newRichClient(sys.env("PG_HOST_PORT"))
}
  
Await.result(client.execute("DROP TABLE IF EXISTS demo"))
```

```tut:book
import com.twitter.util.Await

// execute a query that has no results - i.e. CREATE TABLE, UPDATE, INSERT, DELETE, etc.
val create = Await.result {
  client.execute("CREATE TABLE demo(id serial PRIMARY KEY, foo text)")
}

val insert = Await.result {
  client.execute("INSERT INTO demo(foo) VALUES ('foo')")
}

// execute a query that has results - a function is given to treat the rows
val select = client.select("SELECT * FROM demo") {
  row => row.get[String]("foo")
}

Await.result(select)
```

Since the results of any operation in finagle are typically a `Future`, we use `Await.result` to block and wait for
the result to materialize. This is handy for a demo, but in production code you wouldn't use await. Instead, you would
sequence operations using `flatMap`, and parallelize them using `Future.join` or `Future.sequence`. See the 
[Finagle User Guide](https://twitter.github.io/finagle/guide/index.html) for more information about concurrent
programming with `Future`s.

As you can see, the `SELECT` query includes a function body in a second argument list. This function will receive each
`Row` that results from the query, and the results of the function will be accumulated into the resulting `Future[Seq]`.
`Row` is an interface that allows retrieving values from Postgres rows in a type-safe way using the following interface:

* `get[T](name: String)` - Get the named column from the row as type `T`. Has unspecified behavior (possibly throwing an
  exception) if the column is `NULL` or does not exist.
* `getOption[T](name: String)` - Get the named column as an `Option[T]`. The result will be `None` if the value is `NULL`,
  or the column does not exist
* `getTry[T](name: String)` - Attempt to get the named column as a `Try[T]`. The result will be `Throw` if the value is
  `NULL`, or if the column does not exist, or if the value cannot be decoded to `T`.
* `getAny(name: String)` - Get the named column, using the default Scala type for whatever the Postgres data type of the
  column is.
  
Each of these methods additionally has an overloaded version that takes `Int`, specifying the index of the column rather
than its name.

## `ValueDecoder`

All of the typed methods require an implicit `ValueDecoder[T]`, which is a typeclass that tells finagle-postgres how to
decode the column. Currently, instances are supplied for the following Scala types:

* `String` - for `text`, `char`, `varchar`, `citext`, and other stringy Postgres values (including `ENUM`)
* `Byte`, `Short`, `Int`, `Long` - for the Postgres integers of their corresponding widths
* `Float`, `Double` - for the Postgres floating-point values of their corresponding precision
* `BigDecimal`, `java.math.BigDecimal` - for Postgres `numeric` values of arbitrary precision
* `java.util.UUID` - for Postgres `uuid` values
* `java.time.LocalDate` - for Postgres `date` values
* `java.time.LocalDateTime` - for Postgres `timestamp without time zone` values
* `java.time.Instant`, `java.time.OffsetDateTime`, `java.time.ZonedDateTime` - for Postgres `timestamp with time zone` values
* `java.net.InetAddress` - for Postgres `inet` values
* `Map[String, Option[String]]` - for Postgres `hstore` values

New `ValueDecoder` instances can be specified for other types; take a look at the existing instances for guidance. We
are happy to accept instances for built-in Scala or Java types into finagle-postgres!

Next, read about [Parameterized Queries](04-parameterized-queries.html)

```tut:invisible
Await.result(client.close())
```
