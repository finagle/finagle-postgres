finagle-postgres
================

Postgres database support for finagle.


##### Working with postgres client

	val client = Client(host, username, password, database)

	val f = client.select("select * from users") {row =>
      User(row.getString("email"), row.getString("name"))
    }

    logger.debug("Responded " + f.get)
