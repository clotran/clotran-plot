#!/usr/bin/env bb
;; test/plot_check.bb: properties of clotran.plot's graphs of functions,
;; each against an oracle, nothing judged by eye. clotran (interpreted,
;; CLOTRAN_PATH at this repo's src) makes the scenes and writes
;; test/out/NAME.svg; clj recomputes the functions; this script compares.
;;
;;   on-function  every sample point is (x, f x): y equal to clj's f x
;;                within 1e-12 relative (libm may differ in the last bit)
;;   no-join      no polyline step a..b crosses a known discontinuity d
;;                (a < d <= b): the poles of 1/x and tan x, floor's jumps
;;   pieces       the polylines (of 2 or more points) are as many as the
;;                curve has continuous pieces on -10..10, so nothing that
;;                is continuous was broken
;;   pixel        at 3000 seeded random x where f x is inside the plot
;;                box, the true point is within 1 pixel of a polyline
;;                drawn in the SVG (the box is 520 by 340 in a 600x400
;;                picture)
;;   y-range      the y ranges a person expects, written down below
;;   svg          each SVG is well-formed XML, and its tick labels are
;;                plain decimals of at most 8 characters
;;
;; Usage: bb test/plot_check.bb    Env: CLOTRAN (default ../clotran/clotran)
(require '[babashka.fs :as fs]
         '[babashka.process :as p]
         '[clojure.data.xml :as xml]
         '[clojure.edn :as edn]
         '[clojure.string :as str])

(def here (fs/parent (fs/absolutize *file*)))
(def src (str (fs/normalize (fs/path here ".." "src"))))
(def out (str (fs/path here "out")))
(def clotran (or (System/getenv "CLOTRAN")
                 (str (fs/normalize (fs/path here ".." ".." "clotran" "clotran")))))

(def pi Math/PI)
;; name, source, discontinuities in -10..10, continuous pieces, expected
;; y range (nil: not asserted).
;;   x^2   0 at x = 0 to 100 at x = +-10; no pole, so the whole curve,
;;         and 0 and 100 are ticks already: [0 100].
;;   x^3   -1000..1000, the whole curve: [-1000 1000].
;;   sin   -1..1: [-1 1].
;;   1/x   a pole at 0. Without it the range would be the samples
;;         nearest 0, thousands; the middle 95% of the curve by x is
;;         |x| >= 0.5, where |1/x| <= 2: [-2 2], ticks already.
;;   exp   e^-10 = 4.5e-5 to e^10 = 22026, no pole: the whole curve,
;;         rounded out to its ticks (steps of 5000): [0 25000].
;;   tan   poles at odd multiples of pi/2; its range depends on the
;;         95% cut and is not asserted, only that it is symmetric.
;;   floor jumps at each integer -9..10; a lone point at x = 10.
(def cases
  [["x2" "(fn [x] (* x x))" [] 1 [0.0 100.0]]
   ["x3" "(fn [x] (* x x x))" [] 1 [-1000.0 1000.0]]
   ["sin" "(fn [x] (m/sin x))" [] 1 [-1.0 1.0]]
   ["inv" "(fn [x] (/ 1.0 x))" [0.0] 2 [-2.0 2.0]]
   ["exp" "(fn [x] (m/exp x))" [] 1 [0.0 25000.0]]
   ["tan" "(fn [x] (m/tan x))" (mapv #(* (+ % 0.5) pi) (range -3 3)) 7 nil]
   ["floor" "(fn [x] (m/floor x))" (mapv double (range -9 11)) 20 nil]])

(defn sh [cmd]
  (let [proc (p/process cmd {:out :string :err :string :in ""
                             :extra-env {"CLOTRAN_PATH" src}})
        r (deref proc 600000 ::timeout)]
    (when (= r ::timeout)
      (p/destroy-tree proc) (println "deadline hit:" (first cmd)) (System/exit 1))
    (when-not (zero? (:exit r))
      (println (first cmd) "failed:" (:out r) (:err r)) (System/exit 1))
    (:out r)))

(def fns-src
  (str "(require '[clojure.math :as m])\n(def fns {"
       (str/join "\n " (map (fn [[k s]] (str (pr-str k) " " s)) cases)) "})\n"))

(def tmp (fs/create-temp-dir {:prefix "plot-check"}))
(fs/create-dirs out)

;; clotran: the scenes, and the SVGs into test/out.
(def scenes
  (let [f (str (fs/path tmp "graphs.cljf"))]
    (spit f (str fns-src "(require '[clotran.plot :as plot])\n"
                 "(doseq [[k f] fns]\n"
                 "  (prn [k (plot/graph f)])\n"
                 "  (plot/graph-svg f (str " (pr-str out) " \"/\" k \".svg\")))\n"))
    (into {} (map edn/read-string (filter #(str/starts-with? % "[") (str/split-lines (sh [clotran f])))))))

(def randoms
  (let [r (java.util.Random. 37)]
    (into {} (for [[k] cases] [k (vec (repeatedly 3000 #(- (* 20 (.nextDouble r)) 10)))]))))

;; clj: f at every sample point's x and at the random x.
(def truth
  (let [in (str (fs/path tmp "xs.edn"))
        f (str (fs/path tmp "truth.clj"))]
    (spit in (pr-str (into {} (for [[k] cases]
                                [k {:samples (vec (for [s (:segments (first (:curves (scenes k)))) [x] s] x))
                                    :randoms (randoms k)}]))))
    (spit f (str fns-src
                 "(prn (into {} (for [[k {:keys [samples randoms]}] (read-string (slurp " (pr-str in) "))]\n"
                 "  (let [f (fns k)] [k {:samples (mapv f samples) :randoms (mapv f randoms)}]))))\n"))
    (edn/read-string (sh ["clojure" "-M" f]))))

(defn finite? [y] (and (number? y) (not (Double/isNaN y)) (not (Double/isInfinite y))))

(defn svg-polylines [file]
  (let [doc (xml/parse-str (slurp file))
        els (tree-seq #(seq (:content %)) #(filter map? (:content %)) doc)
        nm #(name (:tag %))]
    {:lines (for [e els :when (= "polyline" (nm e))]
              (mapv #(mapv parse-double (str/split % #",")) (str/split (get-in e [:attrs :points]) #" ")))
     :labels (for [e els :when (= "text" (nm e))] (str/join (:content e)))}))

(defn dist [[px py] [[ax ay] [bx by]]]
  (let [dx (- bx ax) dy (- by ay) l2 (+ (* dx dx) (* dy dy))
        t (if (zero? l2) 0 (max 0 (min 1 (/ (+ (* (- px ax) dx) (* (- py ay) dy)) l2))))
        ex (- px (+ ax (* t dx))) ey (- py (+ ay (* t dy)))]
    (Math/sqrt (+ (* ex ex) (* ey ey)))))

(defn check [[k _ discont pieces want-y]]
  (let [g (scenes k)
        segs (:segments (first (:curves g)))
        pts (vec (apply concat segs))
        fs (get-in truth [k :samples])
        [xa xb] (:x-range g) [ya yb] (:y-range g) [bx by bw bh] (:box g)
        px #(+ bx (* (- % xa) (/ bw (- xb xa))))
        py #(- (+ by bh) (* (- % ya) (/ bh (- yb ya))))
        off (keep (fn [[[x y] fx]] (when-not (or (= y fx) (<= (abs (- y fx)) (* 1e-12 (abs fx)))) [x y fx]))
                  (map vector pts fs))
        joins (for [s segs [[a] [b]] (partition 2 1 s) d discont :when (< a d) :when (<= d b)] [a b d])
        n-pieces (count (filter #(> (count %) 1) segs))
        {:keys [lines labels]} (svg-polylines (str out "/" k ".svg"))
        pieces-px (for [l lines [a b] (partition 2 1 l)] [a b])
        buckets (reduce (fn [m [[ax] [bx'] :as pc]]
                          (reduce #(update %1 %2 conj pc) m
                                  (range (long (Math/floor (- (min ax bx') 2))) (inc (long (Math/floor (+ (max ax bx') 2)))))))
                        {} pieces-px)
        far (keep (fn [[x fx]]
                    (when (and (finite? fx) (<= ya fx yb))
                      (let [P [(px x) (py fx)]
                            d (reduce min ##Inf (map #(dist P %) (buckets (long (Math/floor (P 0))))))]
                        (when (>= d 1) [x fx d]))))
                  (map vector (randoms k) (get-in truth [k :randoms])))
        bad-labels (remove #(and (re-matches #"-?\d+(\.\d+)?" %) (<= (count %) 8)) labels)
        y-ok (if want-y (= want-y (:y-range g)) (= ya (- yb)))
        fails (cond-> []
                (seq off) (conj [:on-function (count off) (first off)])
                (seq joins) (conj [:no-join (count joins) (first joins)])
                (not= pieces n-pieces) (conj [:pieces n-pieces :want pieces])
                (seq far) (conj [:pixel (count far) (apply max-key #(% 2) far)])
                (not y-ok) (conj [:y-range (:y-range g) :want (or want-y :symmetric)])
                (seq bad-labels) (conj [:svg-labels bad-labels]))]
    (println (format "%-6s %4d points, %2d pieces, y %s, ticks %s: %s"
                     k (count pts) n-pieces (:y-range g) (str/join " " labels)
                     (if (seq fails) (str "FAIL " fails) "ok")))
    (empty? fails)))

(let [results (mapv check cases)]
  (fs/delete-tree tmp)
  (println (format "%d of %d graphs pass every check" (count (filter true? results)) (count results)))
  (when-not (every? true? results) (System/exit 1)))
