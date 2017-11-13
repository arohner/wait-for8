(ns circle.wait-for8-test
  (:require [bond.james :as bond]
            [java-time :as time]
            [clojure.spec.test.alpha :as stest]
            [clojure.test :refer :all]
            [slingshot.slingshot :refer (try+ throw+)]
            [circle.wait-for8 :refer :all])
  (:import java.io.IOException))

(stest/instrument (ns-publics 'circle.wait-for8))
(defn foo []
  "foo")

(defn bar []
  "bar")

(deftest catch-dispatch-works
  (are [val e ret] (= ret (catch-dispatch val e))
    {} (IOException.) :default
    {:catch [IOException]} (IOException.) :seq-throwables
    {:catch :foo} {:foo false} :slingshot-keyword
    {:catch [:foo :bar]} {:foo true} :slingshot-vector
    {:catch (fn [t] t)} (IOException.) :fn
    {:catch (fn [t] t)} {:foo true} :fn))

(deftest catch?-works
  (are [opts e ret] (= ret (catch? opts e))
    {} (IOException.) false
    {:catch [IOException]} (IOException.) true
    {:catch :foo} {:foo true} true
    {:catch :foo} {:bar true} false
    {:catch [:foo :bar]} {} false
    {:catch [:foo :bar]} {:foo true} false
    {:catch [:foo :bar]} {:foo :bar} true
    {:catch (fn [t] (:foo t))} {:foo :bar} true))

(defn stateful-fn [vals]
  (let [state (ref vals)]
    (fn []
      (dosync
       (if (seq @state)
         (let [v (first @state)]
           (commute state rest)
           v)
         (assert false "no more values in stateful-fn"))))))

(deftest wait-for-retries
  (is (wait-for {:sleep (time/millis 1)
                 :tries 5}
                (stateful-fn [false false false false true]))))

(deftest wait-for-throws-on-timeout
  (is (thrown? Exception
               (wait-for {:sleep (time/millis 1)
                          :tries 4}
                         (stateful-fn [false false false false true])))))

(deftest wait-for-error-hook
  (is
   (wait-for {:sleep (time/millis 1)
              :tries 10
              :error-hook (fn [e]
                            (printf (format "caught: %s\n" (.getMessage e))))}
             (stateful-fn [false false false false true]))))

(deftest wait-for-success-fn
  (is (= 8 (wait-for {:sleep (time/millis 1)
                      :tries 10
                      :success-fn (fn [v]
                                    (= v 8))}
                     (stateful-fn [0 1 2 3 4 5 6 7 8 9 10])))))

(deftest timeout-works
  (is (thrown? Exception
               (wait-for {:sleep (time/millis 1)
                          :tries 10
                          :timeout (time/millis 400)
                          :success-fn (fn [v]
                                        (= v 42))}
                         (let [f (stateful-fn (range 100))]
                           (fn []
                             (Thread/sleep 100)
                             (f)))))))

(deftest  throws-when-not-ready
  (is (thrown? Exception (wait-for {:sleep (time/millis 1)
                                    :timeout (time/millis 20)}
                                   (stateful-fn (repeatedly false))))))

(deftest does-not-catch-exceptions-by-default
  (with-redefs [foo (fn []
                      (throw (Exception. "fail!")))]
    (bond/with-spy [foo]
      (is (thrown? Exception (wait-for {:tries 5
                                        :sleep (time/millis 1)}
                                       foo)))
      (is (-> foo bond/calls count (= 1))))))

(deftest does-not-catch-slingshots-by-default
  (with-redefs [foo (fn []
                      (throw+ {:test :test}))]
    (bond/with-spy [foo]
      (try+
       (wait-for {:sleep (time/millis 1)
                  :tries 5}
                 foo)
       (is false)
       (catch Object _
         (is true)))
      (is (-> foo bond/calls count (= 1))))))

(deftest retries-on-exception
  (is (= 42 (wait-for {:tries 4
                       :sleep (time/millis 1)
                       :catch [IOException]}
                      (let [f (stateful-fn [#(throw (IOException.))
                                            #(throw (IOException.))
                                            #(throw (IOException.))
                                            #(identity 42)])]
                        (fn [] ((f))))))))

(deftest catches-listed-exceptions
  (is (thrown? ArrayIndexOutOfBoundsException
               (wait-for {:tries 4
                          :sleep (time/millis 1)
                          :catch [IOException]}
                         (fn []
                           (throw (ArrayIndexOutOfBoundsException.)))))))

(deftest error-hook
  (bond/with-spy [foo]
    (wait-for {:tries 2
               :sleep (time/millis 1)
               :catch [IOException]
               :error-hook (fn [e] (foo))}
              (let [f (stateful-fn [#(throw (IOException.)) #(identity true)])]
                (fn [] ((f)))))
    (is (-> foo bond/calls count (= 1)))))

(deftest unlimited-retries
  (is (wait-for {:tries :unlimited
                 :sleep (time/millis 1)
                 :timeout (time/millis 100)}
                (stateful-fn (concat (take 40 (repeat false)) [true])))))

(deftest nil-sleep
  (is (wait-for {:tries 3
              :sleep nil}
             (stateful-fn [false false true]))))

(deftest no-throw
  (is (nil? (wait-for {:tries 3
                       :sleep nil
                       :catch [IOException]
                       :success-fn :no-throw}
                      (let [f (stateful-fn [#(throw (IOException.))
                                            #(throw (IOException.))
                                            #(identity nil)])]
                        (fn [] ((f))))))))

(deftest tries-not-used-when-sleep-and-timeout-are-specified
  (bond/with-stub [foo]
    (is (thrown? Exception
                 (wait-for {:sleep (time/millis 1)
                            :timeout (time/millis 100)}
                           foo)))
    (is (-> foo bond/calls count (> 10)))))

(deftest throws-when-success-fn-isnt-fn
  (bond/with-spy [foo]
    (is (thrown? Exception
                 (wait-for {:success-fn false
                            :sleep (time/millis 1)
                            :tries 5}
                           #(foo))))
    (-> foo bond/calls count (= 0))))

(deftest catch-slingshot-keyword
  (bond/with-stub! [[foo (stateful-fn
                          [#(throw (ex-info "throw foo" {:foo true}))
                           #(identity true)])]]
    (is (= true (wait-for {:sleep (time/millis 1)
                           :catch :foo
                           :tries 5}
                          #((foo)))))
    (is (-> foo bond/calls count (= 2))))
  (testing "falsey"
    (is (thrown? Exception
                 (wait-for {:catch :foo}
                           #(throw (ex-info "threw bar" {:bar true})))))))


(deftest catch-works-with-vectors
  (bond/with-stub! [[foo (stateful-fn
                          [#(throw+ {:foo :bar})
                           #(identity true)])]]
    (is true (wait-for {:sleep (time/millis 1)
                :catch [:foo :bar]
                :tries 5}
               #((foo))))
    (is (= 2 (-> foo bond/calls count))))
  
  (bond/with-stub! [[foo (stateful-fn
                          [#(throw (ex-info "throw :foo :bar" {:foo :bar}))
                           #(identity true)])]]
    (is (wait-for {:catch [:foo :bar]
                   :tries 5}
                  #((foo))))
    (is (= 2 (-> foo bond/calls count))))

  (testing "falsey"
    (is (thrown? Exception
                 (wait-for {:sleep (time/millis 1)
                            :catch [:foo :bar]
                            :tries 5}
                           (stateful-fn [#(throw (ex-info "throw :foo 42" {:foo 42}))]))))))

(deftest catch-fn
  (testing "success"
    (bond/with-stub! [[foo (fn []
                             (throw (Exception. "test")))]]
      (is (thrown? Exception
                   (wait-for {:catch (let [f (stateful-fn [true true false])]
                                       (fn [e] (f)))
                              :sleep (time/millis 1)
                              :tries 5}
                             foo)))
      (is (= 3 (-> foo bond/calls count)))))
  
  (testing "throws"
    (bond/with-stub! [[foo (fn []
                             (throw (Exception. "test")))]]
      (is (thrown? Exception (wait-for {:catch (constantly false)
                                        :tries 5}
                                       foo)))
      (is (= 1 (-> foo bond/calls count))))))
