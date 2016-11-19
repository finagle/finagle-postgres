package com.twitter.finagle.postgres.generic

import scala.util.matching.Regex.Match

trait ColumnNamer extends (String => String) {
  def andThen(g: (String) => String): ColumnNamer = new ColumnNamer {
    def apply(s: String) = g(ColumnNamer.this.apply(s))
  }
}

object ColumnNamer {
  implicit val default: ColumnNamer = snake.snake

  object snake {
    implicit object snake extends ColumnNamer {

      private val camelRegexes = List(
        """([a-z])([A-Z]+)""".r -> ((m: Match) => s"${m.group(1)}_${m.group(2)}"),
        """([A-Z]+)([A-Z])([a-z])""".r -> ((m: Match) => s"${m.group(1)}_${m.group(2)}${m.group(3)}"),
        """([^0-9_])([0-9])""".r -> ((m: Match) => s"${m.group(1)}_${m.group(2)}"),
        """([0-9])([^0-9_])""".r -> ((m: Match) => s"${m.group(1)}_${m.group(2)}")
      )

      def apply(scalaName: String): String = camelRegexes.foldLeft(scalaName) {
        case (str, (regex, replacer)) => regex.replaceAllIn(str, replacer)
      }.toLowerCase()
    }
  }

  object identity {
    implicit object identity extends ColumnNamer {
      def apply(scalaName: String): String = scalaName
    }
  }
}
