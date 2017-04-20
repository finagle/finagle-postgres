---
title: Automatic Case Class Marshalling
layout: default
---

# Automatic `case class` marshalling

As we've seen earlier, `SELECT` queries using the finagle-postgres API must specify a function which operates on a `Row`
and returns some type `T`. The resulting future is then the result of all the rows after being mapped with the given
function: `Future[Seq[T]]`.

This typically results in a pattern like this:

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
Await.result(client.prepareAndExecute("CREATE TABLE demo(id serial PRIMARY KEY, foo text)"))
Await.result(client.prepareAndExecute("INSERT INTO demo (foo) VALUES ($1)", "foo"))
```

```tut:book
case class Demo(id: Int, foo: String)

val select = client.prepareAndQuery("SELECT * FROM demo") {
  row => Demo(
    id = row.get[Int]("id"),
    foo = row.get[String]("foo")
  )
}

Await.result(select)
```

As you can see, this probably gets verbose and repetitive for rows which have a larger number of columns. It looks like
there must be a better way, and thanks to [shapeless](https://github.com/milessabin/shapeless), there is! Importing
`com.twitter.finagle.postgres.generic._` enriches `client` with an additional operation, `queryAs`:

```tut:book
import com.twitter.finagle.postgres.generic._

val result = Await.result {
  client.queryAs[Demo]("SELECT * FROM demo")
}
```

This method automatically decodes the rows into the specified case class. By default, names are converted to snake case
for accessing the fields in the table; this can be overridden by importing `com.twitter.finagle.postgres.generic.ColumnNamer.identity._`
to instead do nothing to convert column names.

Next, read about [Query DSL](06-query-dsl.html)

```tut:invisible
Await.result(client.close())
```