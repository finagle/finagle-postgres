---
title: Query DSL
layout: default
---

# Query DSL

Currently, finagle-postgres offers a rudimentary DSL which provides a slightly nicer syntax for defining and running
queries. The DSL lives in the `com.twitter.finagle.postgres.generic._` import.

The abstraction provided is the `Query[T]` data type, which captures a query and its parameters. It's used in conjunction
with the `QueryContext` implicit enrichment, which provides a `sql` String interpolator:




```scala
import com.twitter.finagle.postgres.generic._
// import com.twitter.finagle.postgres.generic._

def insert(foo: String) = sql"INSERT INTO demo (foo) VALUES ($foo)"
// insert: (foo: String)com.twitter.finagle.postgres.generic.Query[com.twitter.finagle.postgres.Row]

def find(input: String) = sql"SELECT * FROM demo WHERE foo = $input".as[Demo]
// find: (input: String)com.twitter.finagle.postgres.generic.Query[Demo]

Await.result {
  insert("foo demo").exec(client)
}
// res4: Int = 1

Await.result {
  find("foo demo").run(client)
}
// res5: Seq[Demo] = List(Demo(2,foo demo))
```

Using the interpolator `sql` in front of a string results in a `Query[Row]` object, which can later be used with a client
by calling `run` (for queries with results) or `exec` (for queries without results). The interpolated values (marked with
a `$` sign in the SQL string) are automatically parameterized in the query, so they aren't prone to SQL injection attacks.

As shown, you can also call `.as[T]` on the resulting `Query[Row]` to automatically turn it into a `Query[T]`. This works
on any `T` which is a case class, as long as all of its members can be decoded (i.e. all members must have an implicit
`ValueDecoder` instance).

For other types of values (like single-column results, for example) there is also a method `map` which takes a function
from the current type of a query (i.e. `Row` for a freshly created `Query[Row]`) to some other type `T`, and appends the
function to the continuation that will map the rows. For example:

```scala
def count(input: String) = sql"SELECT count(*) FROM demo WHERE foo = $input".map {
  row => row.get[Long]("count")
}
// count: (input: String)com.twitter.finagle.postgres.generic.Query[Long]

Await.result {
  count("foo demo").run(client).map(_.head)
}
// res6: Long = 1
```

This example uses `map` to extract the column `count` from each row (this could also have been done by creating a `case
class` with a `count` column); since there is only one row expected, we also `map` over the resulting future and take
just the first row using `_.head`.

A more in-depth query DSL is planned, but this is the extent of what's currently offered.


