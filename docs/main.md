# finagle-postgres

Finagle-postgres is an asynchronous [PostgreSQL](https://postgresql.org) client library for [Finagle](https://twitter.github.io/finagle).
It provides the following features:

* Native Scala implementation of the PostgreSQL protocol
* Support for PostgreSQL 8+
* Support for binary format parameters and results
* Support for transactions
* Support for prepared/parameterized statements
* An automatic, configurable connection pool based on Finagle standards
* A rich set of data type conversions, which is fully extensible
* Type-safe marshalling of data from Scala to PostgreSQL and back
* Boilerplate-free marshalling of rows to case classes (with the `finagle-postgres-shapeless` module)


## Documentation
To get started with finagle-postgres, check out the [Guide](index.md).

## License

Finagle-postgres is licensed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0)
(the "License"); you may not use this software except in compliance with the License.

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific
language governing permissions and limitations under the License.

