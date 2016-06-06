(defproject ev3dev-lang "0.1.1"
  :description "Clojure wrapper around ev3dev API."
  :url "https://github.com/jeromedane/ev3dev-lang-clj"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :signing {:gpg-key "jerome.dane@gmail.com"}
  :deploy-repositories [["clojars" {:creds :gpg}]]

  :jar-name "ev3dev-lang-clj-%s.jar"

  :dependencies [[org.clojure/clojure "1.7.0-RC1"]
                 [clj-ssh "0.5.11"]]

  :plugins [[codox "0.8.10"]])
