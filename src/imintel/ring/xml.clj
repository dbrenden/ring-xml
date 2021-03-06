(ns imintel.ring.xml
  (:use ring.util.response
        [clojure.java.io :only [input-stream]])
  (:require [clojure.xml :as xml]
            [clojure.zip :as zip])
  (:import [org.xml.sax SAXParseException]))

(defn- xml-request? 
  "Determine if the incoming collection represents an XML request."
  [request] 
  (if-let [type (:content-type request)]
    (not (empty? (re-find #"(application/xml)|(text/xml)" type)))))

(defn- to-xml [data]
  "Convert a valid collection to XML."
  (clojure.string/replace
    (with-out-str 
      (xml/emit-element (zip/root data)))
    #"\n" ""))

(defn- from-xml [request]
  "Verifies an incoming request and consumes the body, parsing it and returning
  the result as an vector of XML elements as maps."
  (if (xml-request? request)
      (if-let [body (:body request)]
        (if-not (coll? body)
          (zip/xml-zip (xml/parse (input-stream body)))))))

(defn- xml-error-response [^Exception e]
  "Convert Exception into response"
  (-> (.getMessage e)
      (response) 
      (status 400)
      (content-type "text/plain")))

(defn wrap-xml-request [handler]
  "Intercepts incoming requests and attempts to parse the body as XML. If 
  successful, will add the resulting XML maps to the :params key, the :xml-params
  key, and the :body."
  (fn [request]
    (try
      (if-let [xml-map (from-xml request)]
        (handler (-> request
                     (assoc :body xml-map)
                     (assoc :xml-params xml-map)
                     (update-in [:params] merge xml-map)))
        (handler request))
      (catch SAXParseException spe (xml-error-response spe)))))

(defn wrap-xml-response [handler]
  "Intercepts outgoing collections and attempts to coerce them into XML."
  (fn [request] 
    (let [response (handler request)]
      (if (coll? (:body response))
        (let [xml-response (update-in response [:body] to-xml)]
          (if (contains? (:headers response) "Content-Type")
            xml-response 
            (content-type xml-response "application/xml; charset=utf-8")))
        response))))