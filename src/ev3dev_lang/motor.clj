(ns ev3dev-lang.motor
  (:require [ev3dev-lang.devices :as devices]
            [clojure.string     :as str]
            [clojure.java.shell :as shell]
            [clojure.string :as string]
            [ev3dev-lang.ssh  :as ssh]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helpers

(def #^{:private true} root-path "/sys/class/tacho-motor/")

(defn- keyword->attr
  "Converts keyword to a file name."
  [k]
  (-> k name (str/replace #"-" "_")))

(defn- str->num [s]
  (when-not (empty? s)
    (. Integer parseInt s)))

(defmulti #^{:private true} send-command (fn [config _ _] (:env config)))

(defmethod #^{:private true} send-command :remote [config id value]
  (let [cmd (str "echo " (name value) " > "
                 root-path
                 id "/command")]
    (ssh/execute (:session config) cmd)))

(defmethod #^{:private true} send-command :local [config id value]
  (spit (str root-path "/"
             id "/command") (name value)))

(defmulti #^{:private true} write-attr (fn [config _ _ _] (:env config)))

(defmethod #^{:private true} write-attr :remote [config id k value]
  (let [cmd (str "echo " value " > " root-path
                 id "/" (keyword->attr k))]
    (println "executing " cmd)
    (ssh/execute (:session config) cmd)))

(defmethod #^{:private true} write-attr :local [config id k value]
  (spit (str root-path id "/" (keyword->attr k)) value))

(defmulti #^{:private true} read-attr (fn [config _ _] (:env config)))

(defmethod #^{:private true}read-attr :remote [config id k]
  (let [cmd (str "cat " root-path "/"
                 id "/" (keyword->attr k))]
    (ssh/execute (:session config) cmd)))

(defmethod #^{:private true} read-attr :local [_ id k]
  (let [path (str root-path id "/" (keyword->attr k))]
    (str/trim-newline (slurp path))))

(defn- locate-in-port
  "Searches through motors directory and either returns
  a matching device's node name or returns nil."
  [config out-port files]
  (first (keep #(let [port (read-attr config % :address)]
                  (when (= port out-port)
                    %)) files)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Commands

(defn address
  "Get the port the motor is plugged into"
  [{:keys [config id]}]
  (read-attr config id :address))

(defn reset
  "Reset all of the parameters back to the default values and stop the motor.

  It's a good idea to send this command at the start of a program to ensure the motor
  is in a known state without having to write each of the parameters individually."
  [{:keys [config id]}]
  (send-command config id :reset))

(defn position
  "Gets or the current position of the motor."
  ([{:keys [config id]}]
   (str->num (read-attr config id :position))))

(defn position-sp
  "Gets or sets the target \"set point\" position of the motor."
  ([{:keys [config id]}]
   (str->num (read-attr config id :position-sp)))
  ([{:keys [config id]} position-value]
   (write-attr config id :position-sp position-value)))

;;;;;; Locating motor ;;;;;;;;

(defn find-motor
  "Finds a motor that is plugged into given port. Available ports are:
  :a, :b, :c, :d. Returns a map defining the motor if found
  on the given port, or nil if no motor was found on the given port."
  [{:keys [env] :as config} port]
  (let [motors (case env
                 :remote (str/split-lines (ssh/execute (:session config) (str "ls " root-path)))
                 :local (str/split-lines (:out (clojure.java.shell/sh "ls" root-path))))]
    (when (> (count motors) 0)
      (when-let [id (locate-in-port config (get devices/ports port) motors)]
        {:id id
         :config config}))))

(defn driver-name
  "Get the driver name of the motor"
  [{:keys [config id]}]
  (read-attr config id :driver-name))

(defn duty-cycle
  "Get the current duty cycle value of a motor. Returns a numerical value.

  The duty cycle is used with speed-regulation \":off\"
  and is useful when you just want to turn
  the motor on and are not too concerned with how stable
  the speed is. The duty cycle attribute accepts values from -100 to +100.
  The sign of the attribute determines the direction of the motor.
  You can update the duty cycle while the motor is running."
  [{:keys [config id]}]
  (str->num (read-attr config id :duty-cycle)))

(defn duty-cycle-sp
  "Get or set the target duty cycle \"set point\" value. Returns a numerical value.

  See `duty-cycle` function for further details."
  ([{:keys [config id]}]
   (str->num (read-attr config id :duty-cycle-sp)))
  ([{:keys [config id]} value]
   {:pre [(>= value 0) (<= value 100)]}
   (write-attr config id :duty-cycle-sp value)))

(defn polarity
  "Changes the forward direction of a motor.

     This is useful, for example, when you have two motors used as drive
     wheels. By changing the polarity of one of the two motors, you can
     send a positive value to both motors to drive forwards.

     Polarity can be set to :normal or :inversed. The default is :normal."
  ([{:keys [config id]}]
   (keyword (read-attr config id :polarity)))
  ([{:keys [config id]} polarity-value]
   {:pre [(some #{polarity-value} [:normal :inversed])]}
   (println "setting polarity to " polarity-value)
   (write-attr config id :polarity (name polarity-value))))

(defn speed
  "Read the current speed of the motor when speed-regulation \":on\"."
  [{:keys [config id]}]
  (str->num (read-attr config id :speed)))

(defn speed-sp
  "Get or set the target \"set point\" speed of a motor, in tacho counts per
  second used with speed-regulation \":on\"."
  ([{:keys [config id]}]
   (str->num (read-attr config id :speed-sp)))
  ([{:keys [config id]} speed]
   (write-attr config id :speed-sp speed)))

(defn speed-regulation
  "Toggle speed-regulation of the motor to :off or :on.
  Default mode is :off.

  :off mode
  In this mode, the motor driver uses the duty cycle
  to determine what percentage of the battery voltage
  to send to the motor.
  If you run the motor at a fairly low duty cycle, and
  you try to stop the hub of the motor with your thumb,
  you'll find it's pretty easy to slow down or even stop
  the motor. In some cases, this is not what you want. You
  want the motor to 'try harder' when it encounters
  resistanceand - and the regulation_mode attribute is
  going to help us with that.

  :on mode
  When the regulation_mode attribute is set to on, the motor
  driver attempts to keep the motor speed at the value you've
  specified.
  You can change the speed by calling set-speed function, e.g.
  (set-speed config id 275)

  If you slow down the motor with a load, the motor driver tries
  to compensate by sending more power to the motor to get it to
  speed up. If you speed up the motor for some reason, the motor
  driver will try to compensate by sending less power to the motor."
  ([{:keys [config id]} mode]
   {:pre [(or (= mode :on) (= mode :off))]}
   (write-attr config id :speed-regulation (name mode)))
  ([{:keys [config id]}]
   (keyword (read-attr config id :speed-regulation))))

(defn state
  "Reading returns a vector of state flags. Possible flags are :running,
  :ramping, :holding, :overloaded and :stalled."
  [{:keys [config id]}]
  (into []
    (map keyword
      (filter #(not (empty? %))
        (string/split (read-attr config id :state) #" ")))))

(defn stop-action
  "Get or set the current stop behavior of a motor. Allowed behaviours are:
  :brake, :coast and :hold.

  :coast - the power will be removed from the motor and it will coast to a stop
  :brake - passive braking; the motor controller removes power from the motor, but it
           also shorts the power wires of the motor together. When a motor is manually
           rotated, it acts as an electrical generator, so shorting the power wires
           creates a load that absorbs energy.
  :hold  - actively hold the motor position when stopping. Instead of removing power
           from the motor, the motor controller will start a PID to prevent the motor
           from being turned any further. It's intended for use with run-to-*-pos
           commands. It will work with other run commands, but may result in unexpected
           behaviour."
  ([{:keys [config id]}]
   (keyword (read-attr config id :stop-command)))
  ([{:keys [config id]} behavior]
   {:pre [(some #{behavior} [:brake :coast :hold])]}
   (write-attr config id :stop-command (name behavior))))

(defn time-sp
  "Get or set the target \"set point\" time in milliseconds to use with run-timed."
  ([{:keys [config id]}]
   (str->num (read-attr config id :time-sp)))
  ([{:keys [config id]} speed]
   (write-attr config id :time-sp speed)))

;;;;;;;;;;;;; Run ;;;;;;;;;;;;;

(defn run-forever
  "Runs the motor at the given port.
  The meaning of `speed` parameter depends on whether the
  speed-regulation is turned on or off.

  When speed-regulation is off (by default) `speed` ranges
  from -100 to 100 and it's absolute value indicates the percent
  of motor's power usage. It can be roughly interpreted as a motor
  speed, but deepending on the environment, the actual speed of the
  motor may be lower than the target speed.

  When speed-regulation is on, which must first be enabled by calling
  `(speed-regulation my-motor :on)`, the motor driver attempts to keep the motor speed at the `speed`
  value you've specified which ranges from -2000 to 2000.

  Negative values indicate reverse motion regardless of speed-regulation."
  ([{:keys [config id]}]
   {:pre [config]}
   (send-command config id :run-forever))
  ([{:keys [config id] :as motor} speed-value]
   {:pre [(integer? speed-value)]}
   (case (speed-regulation motor)
     :on (speed-sp motor speed-value)
     :off (duty-cycle-sp motor speed-value))
   (send-command config id :run-forever)))

(defn run-to-abs-pos
  "Run to an absolute position and then stop using the action specified
  by stop-action."
  ([{:keys [config id]}]
   (send-command config id :run-to-abs-pos))
  ([{:keys [config id] :as motor} position-value]
   (position-sp motor position-value)
   (run-to-abs-pos motor)))

(defn run-to-rel-pos
  "Runs the motor to relative position specified by position-sp or a supplied
  numeric value as a second argument.

  This function can be invoked either without specifying position and using the
  current value, or with a position passed in, e.g.
  (run-to-rel-pos my-motor)
  (run-to-rel-pos my-motor 180)

  NOTE: Using a negative value for a position will cause the motor to rotate in
  the opposite direction."
  ([{:keys [config id]}]
   (send-command config id :run-to-rel-pos))
  ([{:keys [config id] :as motor} position]
   (write-attr config id :position-sp position)
   (run-to-rel-pos motor)))

(defn run-timed
  "Runs a motor for a specified time asynchronously.
  It starts the motor using :run-forever command and sets a timer in the kernel
  to run the :stop command after the specified time. The time is in milliseconds,
  and is written with `time-sp`"
  ([{:keys [config id]}]
   (send-command config id :run-timed))
  ([{:keys [config id] :as motor} time]
   (time-sp motor time)
   (run-timed motor)))

(defn run-direct
  "Runs a motor just like :run-forever, but immediately changes `speed-regulation`
  to :off and causes changes to `duty-cycle-sp` to take effect immediately
  instead of having to send a new run command. To update the duty-cycle
  run command: (duty-cycle-sp my-motor my-speed)

  This is useful for implementing your own PID or something similar that needs to
  update the motor output very quickly."
  ([{:keys [config id] :as motor}]
   (speed-regulation motor :off)
   (send-command config id :run-direct))
  ([{:keys [config id] :as motor} speed-value]
   (duty-cycle-sp motor speed-value)
   (run-direct motor)))

(defn stop
  "Stops the motor:
  (stop config id)

  The recommended way of stopping is to set the behaviout beforehand, e.g.
  (set-stop-behavior config id :coast)
  There are three possible behaviours when the motor stops:
  :coast, :brake and :hold, described in more detail in set-stop-behavior
  docstring.

  We can also pass in required behavior to stop function:
  (stop config id :coast)"
  ([{:keys [config id]}]
   (send-command config id :stop))
  ([{:keys [config id] :as motor} behavior]
   (stop-action motor behavior)
   (send-command config id :stop)))
