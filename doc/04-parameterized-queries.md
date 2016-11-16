---
title: Parameterized Queries
layout: default
---

# Parameterized queries

Since most database-driven applications will need to use user-supplied data as parameters to their queries,
finagle-postgres supports parameterized queries through its prepared statement interface:




```scala
import com.twitter.util.Await
// import com.twitter.util.Await

// execute a query that has no results - i.e. CREATE TABLE, UPDATE, INSERT, DELETE, etc.
val create = Await.result {
  client.prepareAndExecute("CREATE TABLE demo(id serial PRIMARY KEY, foo text)")
}
// create: Int = 1

val insert = Await.result {
  client.prepareAndExecute("INSERT INTO demo(foo) VALUES ($1)", "foo")
}
// insert: Int = 1

// execute a query that has results - a function is given to treat the rows
val result = Await.result {
  client.prepareAndQuery("SELECT * FROM demo WHERE foo = $1", "foo") {
    row => row.get[String]("foo")
  }
}
// result: Seq[String] = List(foo)
```

Here, we used `prepareAndExecute` and `prepareAndQuery` rather than just `execute` and `query`. These methods take any
number of *parameters* in addition to the SQL query. The given parameters are encoded and sent separately from the
query, which means they won't be a potential vector for SQL injection attacks.

Next, read about [Automatic case class marshalling](05-automatic-case-class-marshalling.html)



