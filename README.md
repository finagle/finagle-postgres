## finagle-postgres [![Build Status](https://secure.travis-ci.org/mairbek/finagle-postgres.png)](http://travis-ci.org/mairbek/finagle-postgres)

Postgres database support for finagle.


## Using postgres client

### Adding dependencies

Maven

	<repositories>
		<repository>
			<id>com.github.mairbek</id>
			<url>https://raw.github.com/mairbek/mvn-repo/master/</url>
			<layout>default</layout>
		</repository>
	</repositories>

	<dependency>
		<groupId>com.github.mairbek</groupId>
		<artifactId>finagle-postgres_2.9.2</artifactId>
		<version>0.0.2</version>
		<scope>compile</scope>
	</dependency>

sbt

	resolvers += "com.github.mairbek" at "https://raw.github.com/mairbek/mvn-repo/master"
  
	"com.github.mairbek" % "finagle-postgres" % "0.0.2"

### Connecting to the DB

	val client = Client(host, username, password, database)

### Selecting with simple query

	val f = client.select("select * from users") {row =>
		User(row.getString("email"), row.getString("name"))
	}
	logger.debug("Responded " + f.get)

### Selecting with prepared statement

	val f = for {
		prep <- client.prepare("select * from users where email=$1 and name=$2")
		users <- prep.select("mickey@mouse.com", "Mickey Mouse") {
			row => User(row.getString("email"), row.getString("name"))
		}
	} yield users
	logger.debug("Responded " + f.get)


### Inserting with prepared statement

	val f = for {
		prep <- client.prepare("insert into users(email, name) values ($1, $2)")
		one <- prep.exec("Daisy Duck", "daisy@duck.com")
		two <- prep.exec("Minnie Mouse", "ms.mouse@mouse.com")
	} yield one.affectedRows + two.affectedRows
	
	logger.debug(f.get + " rows affected")

### Updating with prepared statement

	val f = for {
		prep <- client.prepare("update users set name=$1, email=$2 where email='mickey@mouse.com'")
		res <- prep.exec("Mr. Michael Mouse", "mr.mouse@mouse.com")
	} yield res.affectedRows

	logger.debug(f.get + " rows affected")

## Change Log

### 0.0.2
* Prepared statements support
* Async responses logging
* Exceptions handling
* Create table/Drop table support

### 0.0.1
* Clear text authentication support
* Md5 authentication support
* Select, update, insert and delete queries

## License

Licensed under the **[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0)** (the "License");
you may not use this software except in compliance with the License.

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

