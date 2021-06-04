package com.twitter.finagle.postgres

import java.sql.Timestamp
import java.time.Instant

import com.twitter.finagle.{Postgres, Status}
import com.twitter.finagle.postgres.codec.ServerError
import com.twitter.util.{Await, Duration}

object BaseIntegrationSpec {
  val pgTestTable = "finagle_test"
}

abstract class BaseIntegrationSpec(version: String) extends EmbeddedPgSqlSpec {

  val queryTimeout = Duration.fromSeconds(2)

  def getBadClient = {
    Postgres.Client()
      .withCredentials(TestDbUser, TestDbPassword)
      .database(TestDbName)
      .withSessionPool.maxSize(1)
      // TODO
//        .conditionally(useSsl, c => sslHost.fold(c.withTransport.tls)(c.withTransport.tls(_)))
      .newRichClient("badhost:5432")
  }

  def cleanDb(client: PostgresClient): Unit = {
    val dropQuery = client.executeUpdate("DROP TABLE IF EXISTS %s".format(BaseIntegrationSpec.pgTestTable))
    val response = Await.result(dropQuery, queryTimeout)

    response must equal(OK(1))

    val createTableQuery = client.executeUpdate(
      """
      |CREATE TABLE %s (
      | str_field VARCHAR(40),
      | int_field INT,
      | double_field DOUBLE PRECISION,
      | timestamp_field TIMESTAMP WITH TIME ZONE,
      | bool_field BOOLEAN
      |)
    """.stripMargin.format(BaseIntegrationSpec.pgTestTable))
    val response2 = Await.result(createTableQuery, queryTimeout)
    response2 must equal(OK(1))
  }

  def insertSampleData(client: PostgresClient): Unit = {
    val insertDataQuery = client.executeUpdate(
      """
      |INSERT INTO %s VALUES
      | ('hello', 1234, 10.5, '2015-01-08 11:55:12-0800', TRUE),
      | ('hello', 5557, -4.51, '2015-01-08 12:55:12-0800', TRUE),
      | ('hello', 7787, -42.51, '2013-12-24 07:01:00-0800', FALSE),
      | ('goodbye', 4567, 15.8, '2015-01-09 16:55:12+0500', FALSE)
    """.stripMargin.format(BaseIntegrationSpec.pgTestTable))

    val response = Await.result(insertDataQuery, queryTimeout)

    response must equal(OK(4))
  }

  s"A postgres client against Postgresql v$version" should {
    "insert and select rows" in {
      val client = getClient
      cleanDb(client)
      insertSampleData(client)

      val selectQuery = client.select(
        "SELECT * FROM %s WHERE str_field='hello' ORDER BY timestamp_field".format(BaseIntegrationSpec.pgTestTable)
      )(
        identity)

      val resultRows = Await.result(selectQuery, queryTimeout)

      resultRows.size must equal(3)

      // Spot check the first row
      val firstRow = resultRows.head

      firstRow.getOption[String]("str_field") must equal(Some("hello"))
      firstRow.getOption[Int]("int_field") must equal(Some(7787))
      firstRow.getOption[Double]("double_field") must equal(Some(-42.51))
      firstRow.getOption[Instant]("timestamp_field") must equal(Some(
        new Timestamp(1387897260000L).toInstant
      ))
      firstRow.getOption[Boolean]("bool_field") must equal(Some(false))
      firstRow.getOption[String]("bad_column") must equal(None)

    }

    "execute a select that returns nothing" in {
      val client = getClient
      cleanDb(client)

      insertSampleData(client)

      val
      selectQuery = client.select(
        "SELECT * FROM %s WHERE str_field='xxxx' ORDER BY timestamp_field".
          format(

            BaseIntegrationSpec.pgTestTable)
      )(identity)

      val

      resultRows = Await.result(
        selectQuery, queryTimeout)
      resultRows.size must equal(0)
    }


    "update a row" in {
      val client = getClient
      cleanDb(client)
      insertSampleData(client)

      val updateQuery = client.executeUpdate(

        "UPDATE %s SET str_field='hello_updated' where int_field=4567".format(BaseIntegrationSpec.pgTestTable)
      )

      val response = Await.

        result(updateQuery, queryTimeout)

      response must equal(OK(1))

      val selectQuery = client.select(

        "SELECT * FROM %s WHERE str_field='hello_updated'".format(BaseIntegrationSpec.pgTestTable)
      )(

        identity)

      val
      resultRows = Await.result(selectQuery, queryTimeout)

      resultRows.size must equal(1)
      resultRows.head.getOption[String]("str_field") must equal(Some("hello_updated"))
    }


    "delete rows" in {
      val client = getClient
      cleanDb(client)
      insertSampleData(client)

      val updateQuery = client.executeUpdate(
        "DELETE FROM %s WHERE str_field='hello'"
          .format(BaseIntegrationSpec.pgTestTable)
      )

      val response = Await.result(updateQuery, queryTimeout)

      response must equal(OK(3))

      val selectQuery = client.select(
        "SELECT * FROM %s".format(BaseIntegrationSpec.pgTestTable)
      )(identity)

      val resultRows = Await.result(selectQuery, queryTimeout)

      resultRows.size must equal (1)
      resultRows.head.getOption[String]("str_field") must equal(Some("goodbye"))
    }


    "select rows via a prepared query" in {
      val client = getClient
      cleanDb(client)
      insertSampleData(client)

      val preparedQuery = client.prepareAndQuery(
        "SELECT * FROM %s WHERE str_field=$1 AND bool_field=$2".format(BaseIntegrationSpec.pgTestTable),
        Param("hello"),
        Param(true))(identity)

      val resultRows = Await.result(
        preparedQuery,
        queryTimeout
      )

      resultRows.size must equal(2)
      resultRows.foreach {
        row =>
          row.getOption[String]("str_field") must equal(Some("hello"))
          row.getOption[Boolean]("bool_field") must equal(Some(true))
      }
    }

    "execute an update via a prepared statement" in {
      val client = getClient
      cleanDb(client)
      insertSampleData(client)

      val preparedQuery = client.prepareAndExecute(
        "UPDATE %s SET str_field = $1 where int_field = 4567".format(BaseIntegrationSpec.pgTestTable),
        Param("hello_updated")
      )

      val numRows = Await.result(preparedQuery)

      val resultRows = Await.result(client.select(
        "SELECT * from %s WHERE str_field = 'hello_updated' AND int_field = 4567".format(BaseIntegrationSpec.pgTestTable)
      )(identity))

      resultRows.size must equal(numRows)
    }

    "execute an update via a prepared statement using a Some(value)" in {
      val client = getClient
      cleanDb(client)
      insertSampleData(client)


      val preparedQuery = client.prepareAndExecute(
        "UPDATE %s SET str_field = $1 where int_field = 4567".format(BaseIntegrationSpec.pgTestTable),
        Some("hello_updated_some")
      )

      val numRows = Await.result(preparedQuery)

      val resultRows = Await.result(client.select(
        "SELECT * from %s WHERE str_field = 'hello_updated_some' AND int_field = 4567".format(BaseIntegrationSpec.pgTestTable)
      )(identity))

      resultRows.size must equal(numRows)
    }

    "execute an update via a prepared statement using a None" in {
      val client = getClient
      cleanDb(client)
      insertSampleData(client)


      val preparedQuery = client.prepareAndExecute(
        "UPDATE %s SET str_field = $1 where int_field = 4567".format(BaseIntegrationSpec.pgTestTable),
        None: Option[String]
      )

      val numRows = Await.result(preparedQuery)

      val resultRows = Await.result(client.select(
        "SELECT * from %s WHERE str_field IS NULL AND int_field = 4567".format(BaseIntegrationSpec.pgTestTable)
      )(identity))

      resultRows.size must equal(numRows)
    }

    "return rows from UPDATE...RETURNING" in {
      val client = getClient
      cleanDb(client)
      insertSampleData(client)


      val preparedQuery = client.prepareAndQuery(
        "UPDATE %s SET str_field = $1 where int_field = 4567 RETURNING *".format(BaseIntegrationSpec.pgTestTable),
        Param("hello_updated")
      )(identity)

      val resultRows = Await.result(preparedQuery)

      resultRows.size must equal(1)
      resultRows.head.get[String]("str_field") must equal("hello_updated")
    }

    "return rows from DELETE...RETURNING" in {
      val client = getClient
      cleanDb(client)
      insertSampleData(client)

      Await.result(client.prepareAndExecute(
        s"""INSERT INTO ${BaseIntegrationSpec.pgTestTable}
            VALUES ('delete', 9012, 15.8, '2015-01-09 16:55:12+0500', FALSE)"""
      ))

      val preparedQuery = client.prepareAndQuery (
        "DELETE FROM %s where int_field = 9012 RETURNING *".format(BaseIntegrationSpec.pgTestTable)
      )(identity)

      val resultRows = Await.result(preparedQuery)

      resultRows.size must equal(1)
      resultRows.head.get[String]("str_field") must equal("delete")
    }


    "execute an UPDATE...RETURNING that updates nothing" in {
      val client = getClient
      cleanDb(client)
      insertSampleData(client)
      val preparedQuery = client.prepareAndQuery(
        "UPDATE %s SET str_field = $1 where str_field = $2 RETURNING *".format(BaseIntegrationSpec.pgTestTable),
        Param("hello_updated"),
        Param("xxxx")
      )(identity)

      val resultRows = Await.result(preparedQuery)

      resultRows.size must equal(0)
    }

    "execute a DELETE...RETURNING that deletes nothing" in {

      val client = getClient
      cleanDb(client)
      insertSampleData(client)

      val preparedQuery = client.prepareAndQuery(
        "DELETE FROM %s WHERE str_field=$1".format(BaseIntegrationSpec.pgTestTable),
        Param("xxxx")
      )(identity)

      val resultRows = Await.result(preparedQuery)
      resultRows.size must equal(0)
    }

    // this test will fail if the test DB user doesn't have permission
    "create an extension using CREATE EXTENSION" in {
      assume(TestDbUser == "postgres")

      val client = getClient
      val result = client.prepareAndExecute("CREATE EXTENSION IF NOT EXISTS hstore")
      Await.result(result)
    }

    "support multi-statement DDL" in {
      val client = getClient
      val result = client.query("""
        |CREATE TABLE multi_one(id integer);
        |CREATE TABLE multi_two(id integer);
        |DROP TABLE multi_one;
        |DROP TABLE multi_two;
        """.stripMargin)
      Await.result(result)
    }


    "throw a ServerError" when {
      "query has error" in {
        val client = getClient
        cleanDb(client)

        val selectQuery = client.select(
          "SELECT * FROM %s WHERE unknown_column='hello_updated'".format(BaseIntegrationSpec.pgTestTable)
        )(identity)

        a[ServerError] must be thrownBy {
          Await.result(selectQuery, queryTimeout)
        }
      }

      "query in a prepared statement has an error" in {
        val client = getClient
        a [ServerError] must be thrownBy {
          Await.result(client.prepareAndQuery("Garbage query")(identity))
        }
      }

      "prepared query is missing parameters" in {

        val client = getClient
        cleanDb(client)

        val preparedQuery = client.prepareAndQuery(
          "SELECT * FROM %s WHERE str_field=$1 AND bool_field=$2".format(BaseIntegrationSpec.pgTestTable),
          Param("hello")
        )(identity)

        a [ServerError] must be thrownBy {
          Await.result(
            preparedQuery,
            queryTimeout
          )
        }

      }
    }

    "return correct availability information" when {
      "client is good" in {
        val client: PostgresClient = getClient
        client.isAvailable must equal(true)
        client.status must equal(Status.Open)
      }
      "client is bad" in {
        val badClient: PostgresClient = getBadClient
        badClient.isAvailable must equal(false)
        Set(Status.Busy, Status.Closed) must contain (badClient.status)
      }
      "client is closed" in {
        val client: PostgresClient = getClient
        client.close()
        client.isAvailable must equal(false)
        client.status must equal(Status.Closed)
      }
    }
  }
}
