(ns ev3dev-lang.sensors.touch
  (:require [ev3dev-lang.devices :as devices]))

(defn pressed?
  "Returns true if the touch sensor is pressed.
  Returns false otherwise."
  [config sensor]
  (= 1 (devices/read-value config sensor)))

(defn released?
  "Returns true if the touch sensor is released.
  Returns false otherwise."
  [config sensor]
  (= 0 (devices/read-value config sensor)))
