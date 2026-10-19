#!/usr/bin/env bb
;; test/theme_check.bb: themes as data (clotran.plot.theme), each check
;; against what the theme says, run through clotran (interpreted).
;;
;;   custom    test/themes/paper.edn (extends :light; its own ground,
;;             font and palette) draws sin, cos and x/4 on its ground, in
;;             its palette's first three colours in order, in its font,
;;             with light's frame and hyphen minus
;;   palette   a map of only :extends :dark and :palette keeps dark's
;;             ground and draws in the new colour
;;   refused   each malformed theme (test/themes/bad, and three inline
;;             ones) is an error whose message names the theme's file
;;             and the key or fault -- never a silent default
;;   default   a project depending on clotran-plot by deps.edn
;;             (:local/root, no CLOTRAN_PATH) gets :dark, and with a
;;             plot-theme.edn at its root gets that theme
;;
;; Usage: bb test/theme_check.bb    Env: CLOTRAN (default ../clotran/clotran)
(require '[babashka.fs :as fs]
         '[babashka.process :as p]
         '[clojure.data.xml :as xml]
         '[clojure.edn :as edn]
         '[clojure.string :as str])

(def here (fs/parent (fs/absolutize *file*)))
(def root (str (fs/normalize (fs/path here ".."))))
(def path (str root "/src" java.io.File/pathSeparator root "/resources"))
(def out (str (fs/path here "out" "paper")))
(def themes (str (fs/path here "themes")))
(def clotran (or (System/getenv "CLOTRAN")
                 (str (fs/normalize (fs/path here ".." ".." "clotran" "clotran")))))

(defn sh [cmd & {:keys [dir env]}]
  (let [proc (p/process cmd {:out :string :err :string :in "" :dir dir
                             :extra-env env})
        r (deref proc 600000 ::timeout)]
    (when (= r ::timeout)
      (p/destroy-tree proc) (println "deadline hit:" (first cmd)) (System/exit 1))
    (when-not (zero? (:exit r))
      (println (first cmd) "failed:" (:out r) (:err r)) (System/exit 1))
    (:out r)))

(defn bad [f] (str themes "/bad/" f))

;; name, the theme (Clojure source), what its message must contain.
(def refusals
  [["unknown key" (pr-str (bad "unknown-key.edn")) ["unknown-key.edn" "unknown key :colour-scheme"]]
   ["not a colour" (pr-str (bad "bad-colour.edn")) ["bad-colour.edn" ":background is not a colour: \"blu\""]]
   ["opacity above 1" (pr-str (bad "opacity.edn")) ["opacity.edn" ":grid-opacity is not a number from 0 to 1"]]
   ["axes style" (pr-str (bad "axes.edn")) ["axes.edn" ":axes is not :frame or :origin"]]
   ["palette colour" (pr-str (bad "palette.edn")) ["palette.edn" ":palette is not a non-empty vector of colours"]]
   ["empty palette" (pr-str (bad "empty-palette.edn")) ["empty-palette.edn" ":palette is not a non-empty vector"]]
   ["negative stroke" (pr-str (bad "stroke.edn")) ["stroke.edn" ":stroke is not a number above 0"]]
   ["missing keys" (pr-str (bad "missing.edn")) ["missing.edn" "missing :axes"]]
   ["no such built-in" (pr-str (bad "no-builtin.edn")) [":neon" "no such built-in theme" "extended by" "no-builtin.edn"]]
   [":extends type" (pr-str (bad "extends-type.edn")) ["extends-type.edn" ":extends is not"]]
   ["not a map" (pr-str (bad "not-a-map.edn")) ["not-a-map.edn" "not a map"]]
   ["not EDN" (pr-str (bad "not-edn.edn")) ["not-edn.edn" "not EDN"]]
   [":extends cycle" (pr-str (bad "cycle-a.edn")) ["cycle-" "more than 16 deep"]]
   ["bad base" (pr-str (bad "extends-bad.edn")) ["unknown-key.edn" ":colour-scheme" "extended by" "extends-bad.edn"]]
   ["no such file" (pr-str (bad "nope.edn")) ["nope.edn" "no such file"]]
   ["inline opacity" "{:extends :dark :grid-opacity -0.1}" ["theme map" ":grid-opacity is not"]]
   ["not a theme" "42" ["a theme is a keyword, a path or a map"]]
   ["unknown keyword" ":neon" [":neon" "no such built-in theme"]]])

(defn parse-svg [file]
  (let [doc (xml/parse-str (slurp file))
        els (tree-seq #(seq (:content %)) #(filter map? (:content %)) doc)
        of (fn [tag] (filter #(= tag (name (:tag %))) els))]
    {:root (:attrs doc)
     :ground (:fill (:attrs (first (of "rect"))))
     :frame? (boolean (some #(= "none" (:fill (:attrs %))) (of "rect")))
     :strokes (distinct (map #(:stroke (:attrs %)) (of "polyline")))
     :labels (for [e (of "text")] (str/join (:content e)))}))

(fs/create-dirs out)
(def tmp (fs/create-temp-dir {:prefix "theme-check"}))

(def results
  (let [f (str (fs/path tmp "themes.cljf"))]
    (spit f (str "(require '[clotran.plot :as plot] '[clotran.plot.svg :as svg] '[clojure.math :as m])\n"
                 "(def three [m/sin m/cos #(/ % 4)])\n"
                 "(plot/graph-svg three " (pr-str (str out "/multi.svg")) " :theme " (pr-str (str themes "/paper.edn")) ")\n"
                 "(plot/graph-svg #(* % %) " (pr-str (str out "/x2.svg")) " :theme " (pr-str (str themes "/paper.edn")) ")\n"
                 "(plot/graph-svg m/sin " (pr-str (str out "/palette-only.svg")) " :theme {:extends :dark :palette [\"#ff00ff\"]})\n"
                 "(def g (plot/graph m/sin))\n"
                 (str/join (for [[k src] refusals]
                             (str "(prn (try (svg/svg (assoc g :theme " src ")) [" (pr-str k) " :accepted]\n"
                                  "  (catch :default e [" (pr-str k) " (ex-message e)])))\n")))))
    (into {} (map edn/read-string (filter #(str/starts-with? % "[") (str/split-lines (sh [clotran f] :env {"CLOTRAN_PATH" path})))))))

(def default-grounds
  "The ground of a project's graph without and with a plot-theme.edn,
  the project depending on this repo by deps.edn alone."
  (let [proj (str (fs/path tmp "proj"))]
    (fs/create-dirs proj)
    (spit (str proj "/deps.edn") (pr-str {:deps {'clotran/clotran-plot {:local/root root}}}))
    (spit (str proj "/app.cljf") "(require '[clotran.plot :as plot])\n(plot/graph-svg #(* % %) \"o.svg\")\n")
    (let [run #(do (sh [clotran "app.cljf"] :dir proj :env {"CLOTRAN_PATH" ""})
                   (:ground (parse-svg (str proj "/o.svg"))))
          without (run)]
      (spit (str proj "/plot-theme.edn") "{:extends :light :background \"#123456\"}")
      [without (run)])))

(let [paper (parse-svg (str out "/multi.svg"))
      paper-x2 (parse-svg (str out "/x2.svg"))
      pal (parse-svg (str out "/palette-only.svg"))
      fails (cond-> []
              (not= "#fbf8f1" (:ground paper)) (conj [:custom :ground (:ground paper)])
              (not= ["#0b3c5d" "#b22222" "#2e8b57"] (:strokes paper)) (conj [:custom :palette (:strokes paper)])
              (not= "Georgia, serif" (:font-family (:root paper))) (conj [:custom :font (:root paper)])
              (not (and (:frame? paper) (:frame? paper-x2))) (conj [:custom :no-frame])
              (not (some #(str/starts-with? % "-") (:labels paper))) (conj [:custom :minus (:labels paper)])
              (not= ["#0f0f12" ["#ff00ff"]] [(:ground pal) (:strokes pal)]) (conj [:palette (:ground pal) (:strokes pal)])
              (not= ["#0f0f12" "#123456"] default-grounds) (conj [:default default-grounds]))
      refused (for [[k _ wants] refusals
                    :let [msg (results k)]]
                [k msg (and (string? msg) (every? #(str/includes? msg %) wants))])]
  (fs/delete-tree tmp)
  (doseq [[k msg ok] refused]
    (println (format "  %-17s %s %s" k (if ok "refused:" "WRONG:") msg)))
  (println (format "%d of %d malformed themes refused with the right message" (count (filter last refused)) (count refused)))
  (println (if (seq fails) (str "FAIL " fails) "custom theme, palette-only override and project default: ok"))
  (when (or (seq fails) (not-every? last refused)) (System/exit 1)))
