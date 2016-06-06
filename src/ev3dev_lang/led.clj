(ns ev3dev-lang.led
  (:require [ev3dev-lang.devices :as devices]))

(defn max-brightness
  "Reads maximum brightness of the led.
  led must be the result of running: ev3dev-lang.devices/find-led

  Returns a numeric value."
  ([{:keys [config] :as led}]
   {:pre [config]}
   (max-brightness config led))
  ([config led]
   (let [v (devices/read-attr config led :max_brightness)]
     (when-not (empty? v)
       (. Integer parseInt v)))))

(defn brightness
  "Gets or sets the brightness of the led.
  Returns the current brightness of the supplied led if called without
  intensity argument, or sets the led-sensor's brightness to the intensity
  supplied.
  led must be the result of running: ev3dev-lang.devices/find-led
  intensity value should not exceed the maximum value for the led."
  ([{:keys [config] :as led}]
   {:pre [config]}
   (let [v (devices/read-attr config led :brightness)]
     (when-not (empty? v)
       (. Integer parseInt v))))
  ([{:keys [config] :as led} intensity]
   {:pre [config]}
   (devices/write-attr config led :brightness intensity)))

(defn trigger
  "Get or set the current mode. Returns a keyword of the current mode if no new
  mode is suplied or sets the current mode to the mode keyword if given.
  led must be the result of running: ev3dev-lang.devices/find-led

  Triggers can make the LED do interesting things.

  Available modes:

  :none       - manually control the LED with brightness
  :mmc0       - makes the LED blink whenever there is SD card activity.
  :timer      - makes the LED blink. When we change the trigger,
                we get new attributes for controlling the on and
                off times. Times are in milliseconds.
  :heartbeat  - makes the LED blink at a rate proportional to CPU usage.
  :default-on - works just like none except that it turns the LED on
                when the trigger is set.
  :rfkill0    - the RF (radio frequency) kill switch for the built-in
                Bluetooth. It should make the LED indicate if the built-in
                Bluetooth is turned on or not.

  All legoev3-battery-*  options are omitted as they are not useful.
  The batteries (including the rechargeable battery pack) do not have
  a way of telling the EV3 about their state, so it is assumed that the
  batteries are always discharging. Therefore these triggers will
  always turn the LED off."
  ([{:keys [config] :as led}]
   (-> (re-find #"\[\S+\]" (devices/read-attr config led :trigger))
       (clojure.string/replace #"\[" "")
       (clojure.string/replace #"\]" "")
       keyword))
  ([{:keys [config] :as led} mode]
   {:pre (contains? {:none :mmc0 :timer :heartbeat :default-on :rfkill0} mode)}
   (devices/write-attr config led :trigger (name mode))))
