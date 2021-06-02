---
title: Parameterized Queries
layout: default
---

# Parameterized queries

Since most database-driven applications will need to use user-supplied data as parameters to their queries,
finagle-postgres supports parameterized queries through its prepared statement interface:

```scala mdoc:invisible
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

```scala mdoc
import com.twitter.util.Await

// execute a query that has no results - i.e. CREATE TABLE, UPDATE, INSERT, DELETE, etc.
val create = Await.result {
  client.prepareAndExecute("CREATE TABLE demo(id serial PRIMARY KEY, foo text)")
}

val insert = Await.result {
  client.prepareAndExecute("INSERT INTO demo(foo) VALUES ($1)", "foo")
}

// execute a query that has results - a function is given to treat the rows
val select = client.prepareAndQuery("SELECT * FROM demo WHERE foo = $1", "foo") {
  row => row.get[String]("foo")
}

Await.result(select)
```

Here, we used `prepareAndExecute` and `prepareAndQuery` rather than just `execute` and `query`. These methods take any
number of *parameters* in addition to the SQL query. The given parameters are encoded and sent separately from the
query, which means they won't be a potential vector for SQL injection attacks.

Next, read about [Automatic case class marshalling](05-automatic-case-class-marshalling.html)

```scala mdoc:invisible
Await.result(client.close())
```