(ns circle.wait-for8
  (:require [clojure.spec.alpha :as s]
            [java-time :as time]
            [slingshot.slingshot :refer (try+ throw+)]))

(defn parse-args [args]
  (if (map? (first args))
    {:options (first args)
     :f (second args)}
    {:f (first args)}))

(defn catch-dispatch [options throwable]
  (let [{:keys [catch]} options
        throwable? (instance? Throwable throwable)
        caught-map? (or (map? throwable)
                        (instance? clojure.lang.ExceptionInfo throwable))]
    (cond
     (nil? catch) :default
     (and throwable? (sequential? catch) (seq catch) (every? #(isa? % Throwable) catch)) :seq-throwables
     (and caught-map? (keyword? catch)) :slingshot-keyword
     (and caught-map? (vector? catch)) :slingshot-vector
     (fn? catch) :fn)))

(defmulti catch? "decides whether this exception should be caught" catch-dispatch)

(defmethod catch? :default [options throwable]
  false)

(defmethod catch? :seq-throwables [options throwable]
  (let [exceptions (:catch options)]
    (boolean (some (fn [e]
                     (instance? e throwable)) exceptions))))

(defmethod catch? :slingshot-keyword [options throwable]
  (boolean (get throwable (:catch options))))

(defmethod catch? :slingshot-vector [options throwable]
  (let [v (:catch options)]
    (= (get throwable (first v)) (second v))))

(defmethod catch? :fn [options throwable]
  (boolean ((:catch options) throwable)))

(defn retry? [options]
  (let [{:keys [end-time tries]} options]
    (cond
      (and end-time (time/after? (time/instant) end-time)) false
      (and (integer? tries) (<= tries 1)) false
      :else true)))

(defn success? [options result]
  (let [success-fn (-> options :success-fn)]
    (cond
     (= success-fn :no-throw) true
     success-fn (success-fn result)
     (and result (not success-fn)) true
     :else false)))

(s/fdef duration->millis :args (s/cat :d time/duration?) :ret int?)
(defn duration->millis [d]
  (-> d .getSeconds (* 1000)))

(defn fail
  "stuff to do when an iteration fails. Returns new options"
  [options]
  (when (-> options :sleep)
    (Thread/sleep (-> options :sleep duration->millis)))
  (update-in options [:tries] (fn [tries]
                                (if (integer? tries)
                                  (dec tries)
                                  tries))))

(defn wait-for* [{:keys [options f]}]
  (let [timeout (-> options :timeout)]
    (try+
      (let [result (f)]
        (if (success? options result)
          result
          (if (retry? options)
            #(wait-for* {:options (fail options)
                         :f f})
            (throw (ex-info "failed to become ready" (merge options {:f f}))))))
      (catch Object t
        (when-let [hook (-> options :error-hook)]
          (hook t))
        (if (and (catch? options t) (retry? options))
          #(wait-for* {:options (fail options)
                       :f f})
          (throw+))))))

(s/def ::sleep (s/nilable time/duration?))
(s/def ::tries (s/or :int nat-int? :unlimited #{:unlimited}))
(s/def ::timeout time/duration?)
(s/def ::slingshot-tuple (s/tuple keyword? any?))

(defn exception-class? [x]
  (isa? x Exception))

(s/def ::catch (s/or :e (s/coll-of exception-class?) :f fn? :k keyword? :slingshot ::slingshot-tuple))
(s/def ::success-fn (s/or :f fn? :no-throw #{:no-throw}))
(s/def ::error-hook fn?)

(s/def ::wait-options (s/keys :opt-un [::sleep
                                       ::tries
                                       ::timeout
                                       ::catch
                                       ::success-fn
                                       ::error-hook]))
(s/def ::wait-args (s/or :f (s/cat :f fn?)
                         :f-opts (s/cat :opts ::wait-options :f fn?)))
(s/fdef wait-for :args ::wait-args)
(defn wait-for
  "Higher Order Function that controls retry behavior. By default, call f, and retry (by calling again), if f returns falsey.

 - f - a fn of no arguments.

 Options:

 - sleep: how long to sleep between retries, as a joda
   period. Defaults to 1s.

 - tries: number of times to retry before throwing. An integer,
   or :unlimited. Defaults to 3 (or unlimited if timeout is given, and tries is not)

 - timeout: a joda period. Stop retrying when period has elapsed,
   regardless of how many tries are left.

 - catch: By default, wait-for does not catch exceptions. Pass this to specify which exceptions should be caught and retried
     Can be one of several things:
     - a seq of exception classes to catch and retry on
     - an fn of one argument, the thrown exception. Retry if it returns truthy.
     - if the exception is a slingshot throwing a map, can be a
       keyword, or a vector of a key and value, destructuring
       slingshot-style. Retry if the value obtained by destrutcturing is truthy

   If the exception matches the catch clause, wait-for
   retries. Otherwise the exception is thrown.

 - success-fn: a fn of one argument, the return value of f. Stop
   retrying if success-fn returns truthy. If not specified, wait-for
   returns when f returns truthy. May pass :no-throw here, which will
   return truthy when f doesn't throw.

 - error-hook: a fn of one argument, an exception. Called every time
   fn throws, before the catch clause decides what to do with the
   exception. This is useful for e.g. logging."

  {:arglists
   '([fn] [options fn])}
  [& args]
  (when-not (s/valid? ::wait-args args)
    (throw (ex-info "invalid arguments" (s/explain-data ::wait-args args))))
  (let [{:keys [options f] :as parsed-args} (parse-args args)
        {:keys [success-fn timeout tries sleep]
         :or {sleep (time/seconds 1)}} options
        tries (if (and sleep timeout (not tries))
                :unlimited
                (or tries 3))
        end-time (when timeout
                   (time/plus (time/instant) timeout))
        options (-> options
                    (assoc :end-time end-time)
                    (assoc :tries tries)
                    (assoc :sleep sleep))]    

    (trampoline #(wait-for* {:options options :f f}))))
