# sut

![chimney sweep](img/sweep2.jpg)

A framework designed to model and run integration tests against
the media-os microservices architecture. `sut` stands for System Under Test,
and is implemented in [Clojure](http://clojure.org) along with the
[linen](http://github.com/tjb1982/linen) and [flax](http://github.com/tjb1982/flax) languages for
declarative, data-driven test development with easy concurrency control, dependency
injection, and templating allowing test writers to develop clean interfaces in any language,
with self-contained modules for writing tests to "do one thing and do it well" without worrying
about [fixtures](https://medium.com/written-in-code/testing-anti-patterns-b5ffc1612b8b#4be4),
or how the state of the world came to pass.

## Usage

### With `docker-compose`

    docker-compose up

The current suite only runs a smoketest that requests the homepage of each of a list of sites that are each deployed to a set of three
environments. The [sites.yaml](sites.yaml) file can be edited to add or remove sites to be tested. The three environments
supported are qa, stage, and prod:

```
SITES: 
- KSBW 
- WJCL 
- KSBW 
- WPBF 
- WAPT 
- WMTW 
- WXII 
- WPTZ 
- WLWT 
- WDSU 
 
ENVIRONMENTS: 
- parent-domain: stage.htvapps.net 
  timeout: 8.0 
  user: htv 
  pass: mediaos 
- parent-domain: com
  timeout: 3.0 
- template: "%.qa.htvapps.net" 
  match: "%" 
  timeout: 13.0 
  user: htv 
  pass: mediaos 

PAGES:
- /
- /weather
```

The `parent-domain` is used to construct the URL required to make the request the host. Also, the qa and stage environments require basic auth, so those values are also provided here and mapped to the correct requests.

## Technical elaboration

The [docker-compose.yml](docker-compose.yml) and [Dockerfile](Dockerfile) files direct docker to bring up a linux box, install
the system dependencies required to run all of the tests, create a non-root
(wheel) user called "linen," and run the test suite as that user. The test
suite process is kicked off with [leiningen](http://leiningen.org), Clojure's npm-
or maven-like project runner, which exits returning a status code indicating the number of failed tests.

The invocation

    lein run linen.conf.yaml sites.yaml

works by using `lein run`, which is the standard way to run a Clojure project with leiningen.
Leiningen calls the project's `-main` function, whose namespace is declared in the [project.clj](project.clj)
file (i.e., `:main sut.core`). The `-main` function (found in the [core.clj](src/sut/core.clj) file) in this case
takes a variadic list of arguments; however, the first argument is required, and it must
be a configuration file (explained [below](#the-main-configuration-file-linenconfyaml)). The remaining optional arguments are used to make
sequential mutations to the suite's environment (also explained [below](#the-environment-mutation-process)).

### The main configuration file `linen.conf.yaml`

The [main configuration file](linen.conf.yaml) is used to declare the main entry point (i.e., `program: ...`),
some local paths, logging location and configuration, a seed environment, and whether or not to include the
current process's environment in the seed environment (i.e., `merge-global-environment: true`).

#### workspace
The `workspace` entry defines the path to the project's parent directory. Using `docker-compose`,
this can be ignored, because the `WORKSPACE` environment variable provided by `docker-compose.yml`
overrides this value.

#### program
The `program` entry points to the linen source file that contains the main entry point for the suite.

#### clean-log-dir
The `clean-log-dir` entry declares whether or not to recursively empty the log dir before creating new logs.

#### console-log-level
The `console-log-level` declares what log level should be written to the console.

#### logger-name
The name of the logger. If left blank, null, or if the entry doesn't exist, the value used will be "undefined."

#### log-dir
The `log-dir` entry declares the directory where the suite should dump its logs. There are several logs
produced by running the suite (discussed [below](#logs)).

#### env
The `env` entry declared the seed environment.

#### merge-global-environment
The `merge-global-environment` entry declares whether the process's environment should be merged with
the seed environment.


![chimney sweep](img/sweep3.jpg)

### The environment mutation process

In Clojure, standard data-structures are almost exclusively immutable. This helps a lot with managing concurrency. Even
`map`s are immutable, such that in order to "change" the state of a map, one `assoc`iates new entries with the map,
which doesn't change the state of the original map--instead it produces a new map with the new entries, and that map is returned
to calling code. The state of the `sut` environment is handled in much the same way. Each new environment document (yaml or json) is `assoc`iated with the 
previous environment document. Deep mutations are possible to map- or dictionary-like structures like this:

    FOO:
      bar:
        baz: 123
        quux: abc
    
    ---
    
    FOO.bar.lalala: qwerty
    FOO.bar.baz:
      ~(inc: [ ~@FOO.bar.baz ]

which results in a data structure in the environment that looks like:

    FOO:
      bar:
        baz: 124
        quux: abc
        lalala: qwerty

The order of environmental mutation goes like this:

1. The process's environment, if elected to be used, which is overridden by
2. The seed environment declared in the config, which is overridden by
3. Any individual yaml files declared on the command line after the main config


![chimney sweep](img/sweep4.jpg)

### Module development

Modules in linen were designed to mimic modular synthesis in audio digital signal processing. All that means is that a module
should encapsulate a very specific behavior given specific inputs, and produce specific outputs that can be patched into other modules. It's always nice
when a module is designed to be idempotent, but linen doesn't enforce that.

#### Programs and modules
A linen program forms a model of modules patched together designed to make coordinated state changes to the environment. Currently there's only one [program](linen/programs/smoketest.yaml) and one [module](linen/modules/smoketest.yaml) to look at, both called "smoketest." The purpose of the module is to test that the homepages of a set of sites deployed to a set of environments all return an HTTP status code of 200 (OK). The [module itself](linen/modules/smoketest.yaml) `requires` three inputs, two of which have defaults. The `HOST`, `PROTOCOL`, and `CREDENTIALS`. The [program](linen/programs/smoketest.yaml) is designed to map a set of sites and a set of environments in parallel to this module. The effect of this is that all of the sites are queried in parallel and the program won't carry on until all of the individual module "articulations" have returned.

#### Checkpoints
A module is made up of checkpoints. A checkpoint is the atom of module construction. Checkpoints can be run concurrently or sequentially, but can't pass values between themselves (by design, but if necessary you can always pass state with file i/o, etc.). Checkpoints are designed to run and collect state that can be passed from one module to other modules. This particular module doesn't explicitly provide any values, though. It just makes a single request and exits with a 1 or 0 return code. The `assert: true` entry declares this module to be a test. By default, when a module is `assert`ed, it checks for a non-zero return value, but there is a way to define a function that takes arguments of `[ out, err, exit ]` and provides a `success` value based on whatever criteria make sense. 


![chimney sweep](img/sweep5.jpg)

### Logs

#### linen.log
This is the DEBUG-level output from linen.

#### raw.json
Running the suite essentially "realizes" the data structure declared as the program (i.e., code as data and vice versa), and yields an in-memory
data structure that can be queried/reduced with post-processing. The `raw.json` is a json-serialization of
this data-structure.

#### checkpoints.json
Every module consists of a synchronized arrangement of checkpoints. Not all checkpoints are tests, but
all tests are checkpoints. This is described in more detail below in the discussion on module development.

#### failures.json
This file consists of only the checkpoints that were deemed to have failed. Only checkpoints that are
`assert`ed can pass or fail.

#### env.json
This is a serialization of the environment that was created just before running the suite.

![chimney sweep](img/sweep.jpg)

## License

Copyright © 2016 Hearst Television, Inc.

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
