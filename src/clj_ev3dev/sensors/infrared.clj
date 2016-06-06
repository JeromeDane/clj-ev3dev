(ns ev3dev-lang.sensors.infrared
  (:require [ev3dev-lang.devices :as devices]))

(defn read-proximity
  "Reads the proximity value (in range 0 - 100)
  reported by the infrared sensor. A value of 100
  corresponds to a range of approximately 70 cm.

  If you're not sure in which mode the sensor currently
  operates, you can check it by running:
  (devices/read-mode config sensor)

  To change the mode of the sensor to one of the followng:
  :ir-prox   Proximity
  :ir-seek   IR seeker
  :ir-remote IR remote control (button)
  :ir-rem-a  IR remote control
  :ir-s-alt  Alternate IR seeker,

  please run:
  (devices/write-mode config sensor :ir-prox)"
  [config sensor]
  (devices/read-value config sensor))
