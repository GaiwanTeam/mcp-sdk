(ns co.gaiwan.lib.hato-charred
  "Monkey patch Hato to read/write JSON using Charred"
  #_(:require
     hato.middleware
     hato.conversion
     charred.api))

;; (defmethod hato.conversion/decode :application/json
;;   [resp _]
;;   (charred.api/read-json (:body resp) :key-fn keyword))

;; (intern 'hato.middleware 'json-enabled? true)
;; (intern 'hato.middleware 'json-encode charred.api/write-json-str)
