package com.twitter.finagle.postgres.connection

import com.twitter.finagle.postgres.Spec
import com.twitter.finagle.postgres.messages.SingleMessageResponse
import com.twitter.finagle.postgres.messages.{NoticeResponse, NotificationResponse, ParameterStatus, Query}

class ConnectionAsyncSpec extends Spec {
  "A postgres connection" should {
    "ignore async messages for new connection but accept notification" in {
      verify(new Connection())
    }

    "ignore async messages for connected client but accept notification" in {
      verify(new Connection(Connected))
    }

    "ignore async messages when in query but accept notification" in {
      val connection = new Connection(Connected)
      connection.send(Query("select * from Test"))
      verify(connection)
    }
  }

  private def verify(connection: Connection) = {
    val response2 = connection.receive(NoticeResponse(Some("blahblah")))
    response2 must equal(None)

    val response3 = connection.receive(ParameterStatus("foo", "bar"))
    response3 must equal(None)

    val not = NotificationResponse(-1, "", "")
    val response4 = connection.receive(not)
    response4 must equal(Some(SingleMessageResponse(not)))
  }
}
