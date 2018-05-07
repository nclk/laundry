# sut

![chimney sweep](img/sweep2.jpg)

A framework designed to model and run integration tests against
the media-os microservices architecture. `sut` stands for System Under Test,
and is implemented in [Clojure](http://clojure.org) along with
[linen](http://github.com/tjb1982/linen) for
declarative, data-driven test development with easy concurrency control, dependency
injection, and templating allowing test writers to develop clean interfaces in any language,
with self-contained modules for writing tests to "do one thing and do it well" without worrying
about [fixtures](https://medium.com/written-in-code/testing-anti-patterns-b5ffc1612b8b#4be4),
or how the state of the world came to pass.

## Usage

### With `docker-compose`

    docker-compose up

The `sut` API is a ReST service over HTTP. After you've modified the provided `docker-compose.yml.template` file to create your own `docker-compose.yml` file with the appropriate modifications, the above `docker-compose up` command will bring up the sut API and its database server (sutdb, a postgres instance). It will also run the db migrations necessary for the service to run. The data is persisted in the repo's `./data` directory by default. If you want to change that so it maps to another directory, it can be done by altering the `volumes` directive in the `docker-compose` file.

Once the system is up and running, you should be able to reach the API by going to its localhost port, e.g.:

    http://localhost:8080/api/v1

The API is self-documenting.

### Web Interface

In order for the Web Interface to work, you will need to provide SSL certs. The docker-compose.yml.template file provides a template for what environment variables are expected. To deploy the docker containers on a "real" server with a domain, you may find [this](https://thetower.atlassian.net/wiki/display/PMOTEST/Quickstart+guide+for+configuring+Let%27s+Encrypt+with+certbot+and+nginx) useful.


![chimney sweep](img/sweep3.jpg)

#### Modules and Checkpoints
A module is made up of checkpoints. A checkpoint is the atom of module construction. Checkpoints can be run concurrently or sequentially, but can't pass values between themselves (by design, but if necessary you can always pass state with file i/o, etc.). Checkpoints are designed to run and pass values from one module to other, child modules. All of the modules currently live [here](https://github.com/hearsttv/sutapi/tree/master/resources/linen/modules). By default, when a module is `assert`ed, it checks for a non-zero return value, but there is a way to define a function that takes arguments of `[ out, err, exit ]` and provides a `success` value based on whatever criteria make sense. 

![chimney sweep](img/sweep5.jpg)

### Logs

#### linen.log
This is the DEBUG-level output from linen, and is accessible from the API at the `/log-entries/` endpoint.

#### raw.json
Running the suite essentially "realizes" the data structure declared as the program (i.e., code as data and vice versa), and yields an in-memory
data structure that can be queried/reduced with post-processing. The `raw.json` is a json-serialization of
this data-structure.

#### checkpoints.json
Every module consists of a synchronized arrangement of checkpoints. Not all checkpoints are tests, but
all tests are checkpoints. This is described in more detail below in the discussion on module development. These are available from the API via the `/checkpoints/` endpoint.

#### failures.json
This file consists of only the checkpoints that were deemed to have failed. Only checkpoints that are
`assert`ed can pass or fail. Since failures are just checkpoints, you can find these via the API at the `/checkpoints/?success=false` endpoint.

#### env.json
This is a serialization of the environment that was created just before running the suite. This is available as an entry of the test runs themselves via the API at `/test-runs/`.

![chimney sweep](img/sweep.jpg)

## License

Copyright © 2016 Hearst Television, Inc.

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
