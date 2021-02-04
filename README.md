wait-for
========

[![Clojars Project](https://img.shields.io/clojars/v/arohner/wait-for8.svg)](https://clojars.org/arohner/wait-for8)


A Higher Order Function that provides declarative retrying.

`wait-for8` is a port of `arohner/wait-for`, using Java 8 instants and
durations rather than joda time.

Let's start with a simple example.

```clojure
(:require [circle.wait-for8 :refer (wait-for)])

(wait-for #(unreliable-fn :foo :bar))

```
This calls unreliable-fn, up to 3 times, returning as soon as unreliable-fn returns truthy.

Options
-------
wait-for has two signatures:
`(wait-for f)` and `(wait-for options f)`. Options is a map. f is always a fn of no arguments.

By default, call f, and retry (by calling again), if f returns falsey.

 - f - a fn of no arguments.

Terminating:

By default, wait-for terminates if `f` returns truthy. It returns the
successful value, or throws if f never returned truthy.

The number of retries can be specified with the options `:tries` and
`:timeout`. If both are specified, the first one to trigger will cause
termination.

If you want to wait for a specific return value, use the `:success-fn`
option.

Options:

 - sleep: how long to sleep between retries, either as a java8
   duration or as a no-arg function returning a long millis value. The function
   will be called after each failure to retrieve a new value.
   A call to `(exponential-backoff-fn)` will create a suitable fn
   to implement exponential backoff with jitter strategy.
  
   Defaults to 1s.

 - tries: number of times to retry before throwing. An integer,
   or `:unlimited`. Defaults to 3 (or unlimited if `:timeout` is given, and `:tries` is not)

 - timeout: a java8 duration. Stop retrying when period has elapsed,
   regardless of how many tries are left.

 - catch: By default, wait-for does not catch exceptions. Pass this to specify which exceptions should be caught and retried
     Can be one of several things:
     - a collection of exception classes to catch and retry on
     - a fn that takes one argument, the thrown exception. Retry if the fn returns truthy.
     - if the exception throws `ex-info`, catch can be a
       keyword, or a vector of a key and value, destructuring
       slingshot-style. Retry if the value obtained by destructuring is truthy

   If the exception matches the catch clause, wait-for
   retries. Otherwise the exception is thrown.

 - success-fn: a predicate, will be called with the return value of
   `f`. Stop retrying if success-fn returns truthy. If not specified,
   wait-for returns when `(f)` returns truthy. May pass `:no-throw`
   here, which will return truthy when f doesn't throw.

 - error-hook: a fn of one argument, an exception. Called every time
   fn throws, before the catch clause decides what to do with the
   exception. This is useful for e.g. logging.

Inspired by [Robert Bruce](https://github.com/joegallo/robert-bruce)

License
-------
EPL 1.0, the same as Clojure
