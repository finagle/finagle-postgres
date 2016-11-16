---
title: Automatic Case Class Marshalling
layout: default
---

# Automatic `case class` marshalling

As we've seen earlier, `SELECT` queries using the finagle-postgres API must specify a function which operates on a `Row`
and returns some type `T`. The resulting future is then the result of all the rows after being mapped with the given
function: `Future[Seq[T]]`.

This typically results in a pattern like this:




```scala
case class Demo(id: Int, foo: String)
// defined class Demo

val result = Await.result {
  client.prepareAndQuery("SELECT * FROM demo") {
    row => Demo(
      id = row.get[Int]("id"),
      foo = row.get[String]("foo")
    )
  }
}
// result: Seq[Demo] = List(Demo(1,foo))
```

As you can see, this probably gets verbose and repetitive for rows which have a larger number of columns. It looks like
there must be a better way, and thanks to [shapeless](https://github.com/milessabin/shapeless), there is! Importing
`com.twitter.finagle.postgres.generic._` enriches `client` with an additional operation, `queryAs`:

```scala
import com.twitter.finagle.postgres.generic._
// import com.twitter.finagle.postgres.generic._

val result = Await.result {
  client.queryAs[Demo]("SELECT * FROM demo")
}
// result: Seq[Demo] = List(Demo(1,foo))
```

This method automatically decodes the rows into the specified case class. By default, names are converted to snake case
for accessing the fields in the table; this can be overridden by importing `com.twitter.finagle.postgres.generic.ColumnNamer.identity._`
to instead do nothing to convert column names.

Next, read about [Query DSL](06-query-dsl.html)



