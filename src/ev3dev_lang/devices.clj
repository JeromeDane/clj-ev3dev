(ns ev3dev-lang.devices
  (:require [clojure.string  :as str]
            [clojure.java.io :as io]
            [ev3dev-lang.ssh  :as ssh]))

(def paths {:touch       "/sys/class/lego-sensor/"
            :color       "/sys/class/lego-sensor/"
            :infrared    "/sys/class/lego-sensor/"
            :red-left    "/sys/class/leds/"
            :red-right   "/sys/class/leds/"
            :green-left  "/sys/class/leds/"
            :green-right "/sys/class/leds/"})

(def type-resolver {:touch       "lego-ev3-touch"
                    :color       "lego-ev3-color"
                    :infrared    "lego-ev3-ir"
                    :green-left  "ev3:left:green:ev3dev"
                    :green-right "ev3:right:green:ev3dev"
                    :red-left    "ev3:left:red:ev3dev"
                    :red-right   "ev3:right:red:ev3dev"})

(def default-ports {:touch    "in1"
                    :color    "in3"
                    :infrared "in4"})

(def ports {:a     "outA"
            :b     "outB"
            :c     "outC"
            :d     "outD"
            :one   "in1"
            :two   "in2"
            :three "in3"
            :four  "in4"})

(def modes {:color    #{:col-color :col-ambient :col-reflect}
            :infrared #{:ir-prox :ir-seek :ir-remote :ir-rem-a :ir-s-alt}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helpers

(defn- str->keyword
  "Converts a string to lower case keyword with
  all spaces repalced with underscores."
  [s]
  (-> s
      (str/replace #" " "_")
      (str/lower-case)
      keyword))

(defn- keyword->str
  "Converts keyqord to upper-case string."
  [k]
  (-> k
      name
      str/upper-case))

(defn- build-path
  "Creates a path to an attribute k, where k
  is a keyword.
  Returns a string."
  [sensor k]
  (str (get paths (:device-type sensor)) (:node sensor) "/" (name k)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Attributes

(defmulti write-attr (fn [config _ _ _] (:env config)))

(defmethod write-attr :remote [config sensor attr value]
  (let [cmd (str "echo \"" value "\" > " (get paths (:device-type sensor))
                 (:node sensor) "/" (name attr))]
    (ssh/execute (:session config) cmd)))

(defmethod write-attr :local [config sensor attr value]
  (spit (str (get paths (:device-type sensor)) (:node sensor) "/" (name attr)) value))

(defmulti read-attr
  (fn [config _ _] (:env config)))

(defmethod read-attr :remote [config sensor attr]
  (let [cmd (str "cat " (get paths (:device-type sensor))
                 (:node sensor) "/" (name attr))]
    (ssh/execute (:session config) cmd)))

(defmethod read-attr :local [config sensor attr]
  (str/trim-newline (slurp (build-path sensor attr))))

(defn read-port-name [config sensor]
  (read-attr config sensor :port_name))

(defn read-driver-name [config sensor]
  (read-attr config sensor :driver_name))

(defn read-value
  "Reads current status of the sensor. Returns a numeric value."
  [config sensor]
  (let [v (read-attr config sensor :value0)]
    (when-not (empty? v)
      (. Integer parseInt v))))

(defn read-mode
  "Reads current mode of the sensor. Returns a keyword."
  [config sensor]
  (str->keyword (read-attr config sensor :mode)))

(defn read-units
  "Returns the units in which the sensor oeprates."
  [config sensor]
  (str->keyword (read-attr config sensor :units)))

(defn- valid-mode? [sensor mode]
  (contains? (get modes (:device-type sensor)) mode))

(defn write-mode
  "Changes the mode of a sensor."
  [config sensor mode]
  (if (valid-mode? sensor mode)
    (write-attr config sensor :mode (keyword->str mode))
    (throw (Exception. "Please provide a valid mode for this sensor."))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Mapping

(defn- port-name [sensor]
  (str "cat /sys/class/lego-sensor/" sensor "/port_name"))

(defn- type-name [sensor]
  (str "cat /sys/class/lego-sensor/" sensor "/driver_name"))

(defn locate-in-port
  "Searches through devices directory and either returns
  a matching device's node name or returns nil."
  ([{:keys [session]} device files]
   (first (keep #(let [port  (ssh/execute session (port-name %))
                       typ   (ssh/execute session (type-name %))
                       class (get type-resolver (:device-type device))]
                   (when (and (= (:port device) port)
                              (= class typ))
                     %)) files)))
  ([config device]
   (let [dir (io/file (get paths (:device-type device)))]
     (first (keep #(when (.isDirectory %)
                     (let [n (str/trim-newline (.getName %))]
                       (when (.startsWith n "sensor")
                         (let [port  (read-port-name config (assoc device :node n))
                               typ   (read-driver-name config (assoc device :node n))
                               class (get type-resolver (:device-type device))]
                           (when (and (= (:port device) port)
                                      (= class typ))
                             n))))) (file-seq dir))))))

(defmulti find-device (fn [config _] (:env config)))

(defmethod find-device :local [config device]
  (let [{:keys [device-type port]} device
        port                       (if port (get ports port) (get default-ports device-type))]
    {:device-type device-type :node (locate-in-port config (assoc device :port port))}))

(defmethod find-device :remote [config device]
  (let [{:keys [device-type port]} device
        files                      (str/split-lines (ssh/execute (:session config) "ls /sys/class/lego-sensor"))
        port                       (if port (get ports port) (get default-ports device-type))]
    {:device-type device-type :node (locate-in-port config (assoc device :port port) files)}))

(defn find-sensor
  "Finds sensor's node name by searching for
  sensor type and port that it's plugged in.

  Config map must contain :env key, which is either
  :remote or :local. If remote environment is used, session
  should be associated into the config map as well.

  Sensor should be a map containing device-type and port.

  Sensor types: :touch, :color, :infrared.
  Ports are: :one, :two, :three, :four."
  [config sensor]
  {:pre [(:env config)
         (or (= (:env config) :local)
             (and (= (:env config) :remote) (not (nil? (:session config)))))]}

  (let [device (find-device config sensor)]
    (if (:node device)
      device
      (throw (Exception. "Could not locate the device. Please check the ports.")))))

(defn find-led
  "Returns node name for a given led color and side.
  Leds are static (they don't change their location on
  the brick) so it always returns the same mapping.

  Led types: :green-left, :green-right, :red-left, :red-right."
  ([led-type]
   {:device-type led-type :node (get type-resolver led-type)})
  ([led-type config]
   (assoc (find-led led-type) :config config)))
