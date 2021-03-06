(ns ring.swagger.json-schema-test
  (:require [midje.sweet :refer :all]
            [schema.core :as s]
            [ring.swagger.json-schema :refer :all]
            [ring.swagger.core :refer [with-named-sub-schemas]]
            [flatland.ordered.map :refer :all])
  (:import [java.util Date UUID]
           [org.joda.time DateTime LocalDate]
           [java.util.regex Pattern]))

(s/defschema Model {:value String})

(facts "type transformations"
  (facts "java types"
    (->swagger Integer)   => {:type "integer" :format "int32"}
    (->swagger Long)      => {:type "integer" :format "int64"}
    (->swagger Double)    => {:type "number" :format "double"}
    (->swagger Number)    => {:type "number" :format "double"}
    (->swagger String)    => {:type "string"}
    (->swagger Boolean)   => {:type "boolean"}
    (->swagger Date)      => {:type "string" :format "date-time"}
    (->swagger DateTime)  => {:type "string" :format "date-time"}
    (->swagger LocalDate) => {:type "string" :format "date"}
    (->swagger Pattern)   => {:type "string" :format "regex"}
    (->swagger #"[6-9]")  => {:type "string" :pattern "[6-9]"}
    (->swagger UUID)      => {:type "string" :format "uuid"})

  (fact "schema types"
    (->swagger s/Int)     => {:type "integer" :format "int64"}
    (->swagger s/Str)     => {:type "string"}
    (->swagger s/Num)     => {:type "number" :format "double"})

  (fact "containers"
    (->swagger [Long])    => {:type "array" :items {:format "int64" :type "integer"}}
    (->swagger #{Long})   => {:type "array" :items {:format "int64" :type "integer"} :uniqueItems true})

  (facts "nil"
    (->swagger nil)       => nil)

  (facts "unknowns"
    (fact "throw exception by default"
      (->swagger java.util.Vector) => (throws IllegalArgumentException))
    (fact "are ignored with *ignore-missing-mappings*"
      (binding [*ignore-missing-mappings* true]
        (->swagger java.util.Vector)) => nil))

  (facts "models"
    (->swagger Model) => {:$ref "#/definitions/Model"}
    (->swagger [Model]) => {:items {:$ref "#/definitions/Model"}, :type "array"}
    (->swagger #{Model}) => {:items {:$ref "#/definitions/Model"}, :type "array" :uniqueItems true})

  (fact "schema predicates"
    (fact "s/enum"
      (->swagger (s/enum :kikka :kakka)) => {:type "string" :enum [:kikka :kakka]}
      (->swagger (s/enum 1 2 3))         => {:type "integer" :format "int64" :enum (seq #{1 2 3})})

    (fact "s/maybe"
      (fact "uses wrapped value by default"
        (->swagger (s/maybe Long)) => (->swagger Long))
      (fact "adds allowEmptyValue when for query and formData as defined by the spec"
        (->swagger (s/maybe Long) {:in :query}) => (assoc (->swagger Long) :allowEmptyValue true)
        (->swagger (s/maybe Long) {:in :formData}) => (assoc (->swagger Long) :allowEmptyValue true))
      (fact "uses wrapped value for other parameters"
        (->swagger (s/maybe Long) {:in :body}) => (->swagger Long)
        (->swagger (s/maybe Long) {:in :header}) => (->swagger Long)
        (->swagger (s/maybe Long) {:in :path}) => (->swagger Long)))

    (fact "s/both -> type of the first element"
      (->swagger (s/both Long String))   => (->swagger Long))

    (fact "s/either -> type of the first element"
      (->swagger (s/either Long String)) => (->swagger Long))

    (fact "s/named -> type of schema"
      (->swagger (s/named Long "long"))  => (->swagger Long))

    (fact "s/one -> type of schema"
      (->swagger [(s/one Long "s")])  => (->swagger [Long]))

    (fact "s/recursive -> type of internal schema"
      (->swagger (s/recursive #'Model))  => (->swagger #'Model))

    (fact "s/eq -> type of class of value"
      (->swagger (s/eq "kikka"))         => (->swagger String))

    (fact "s/Any -> nil"
      (->swagger s/Any) => nil)))

(fact "Describe"
  (tabular
    (fact "Basic classes"
      (let [schema (describe ?class ..desc.. :minimum ..val..)]
        (json-schema-meta schema) => {:description ..desc.. :minimum ..val..}
        (->swagger schema) => (contains {:description ..desc..})))
    ?class
    Long
    Double
    String
    Boolean
    Date
    DateTime
    LocalDate
    Pattern
    UUID
    clojure.lang.Keyword)

  (fact "Describe Model"
    (let [schema (describe Model ..desc..)]
      (json-schema-meta schema) => {:description ..desc..}
      (->swagger schema) => (contains {:description ..desc..})
      )))

(facts "properties"
  (fact "s/Any -values are ignored"
    (keys (properties {:a String
                       :b s/Any}))
    => [:a])

  (fact "s/Keyword -keys are ignored"
    (keys (properties {:a String
                       s/Keyword s/Any}))
    => [:a])

  (fact "with unknown mappings"
    (fact "by default, exception is thrown"
      (properties {:a String
                   :b java.util.Vector}) => (throws IllegalArgumentException))
    (fact "unknown fields are ignored ig *ignore-missing-mappings* is set"
      (binding [*ignore-missing-mappings* true]
        (keys (properties {:a String
                           :b java.util.Vector})) => [:a])))

  (fact "Keeps the order of properties intact"
    (keys (properties (ordered-map :a String
                                   :b String
                                   :c String
                                   :d String
                                   :e String
                                   :f String
                                   :g String
                                   :h String)))
    => [:a :b :c :d :e :f :g :h])

  (fact "Ordered-map works with sub-schemas"
    (properties (with-named-sub-schemas (ordered-map :a String
                                                     :b {:foo String}
                                                     :c [{:bar String}])))
    => anything)

  (fact "referenced record-schemas"
    (s/defschema Foo (s/enum :one :two))
    (s/defschema Bar {:key Foo})

    (fact "can't get properties out of record schemas"
      (properties Foo)) => (throws AssertionError)

    (fact "nested properties work ok"
      (keys (properties Bar)) => [:key])))
