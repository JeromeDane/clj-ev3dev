(ns clj-ev3dev-exampleapp.core
  (:require [ev3dev-lang.led              :as led]
            [ev3dev-lang.devices          :as devices]
            [ev3dev-lang.motors.tacho     :as tacho]
            [ev3dev-lang.sensors.color    :as color]
            [ev3dev-lang.sensors.infrared :as infrared]
            [ev3dev-lang.sensors.touch    :as touch])
  (:gen-class :main true))

(defn test-leds [config]
  (let [left-red    (devices/find-led :red-left)
        left-green  (devices/find-led :green-left)
        right-red   (devices/find-led :red-right)
        right-green (devices/find-led :green-right)]
    (println "LEDS")
    (led/set-trigger config left-red :heartbeat)
    (led/set-trigger config left-red :none)
    (led/set-trigger config left-green :default-on)))

(defn test-motors [config]
  (println "MOTORS")
  (let [left-motor  (tacho/find-tacho-motor config :b)
        right-motor (tacho/find-tacho-motor config :c)]
    (tacho/run-forever config left-motor 20)
    (tacho/run-forever config right-motor 20)
    (tacho/stop config left-motor)
    (tacho/stop config right-motor)))

(defn test-color-sensor [config]
  (println "COLOR")
  (let
  [color-sensor (devices/find-sensor config {:device-type :color :port :one})]
    (devices/write-mode config color-sensor :col-color)
    (println "Color: " (color/read-color config color-sensor))))

(defn test-color-sensor [config]
  (println "COLOR")
  (let
  [color-sensor (devices/find-sensor config {:device-type :color :port :one})]
    (devices/write-mode config color-sensor :col-color)
    (println "Color: " (color/read-color config color-sensor))))

(defn test-infrared-sensor [config]
  (println "INFRARED")
  (let [infrared-sensor (devices/find-sensor config {:device-type :infrared :port :four})]
    (println "Proximity: " (infrared/read-proximity config infrared-sensor))))

(defn -main
  "The application's main function"
  [& args]
  (let [config {:env :local}]
    (test-leds config)
    (test-motors config)
    (test-color-sensor config)
    (test-infrared-sensor config)))
