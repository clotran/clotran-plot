#!/usr/bin/env bb
;; test/surface_check.bb: clotran.plot's surfaces and heat maps, each
;; property against an oracle. clotran (interpreted) makes the scenes and
;; writes test/out/surface/NAME.svg; clj recomputes f at every grid
;; point; this script projects the box itself and compares.
;;
;;   on-function  every grid z equals clj's f(x, y) within 1e-12
;;                relative, and is nil exactly where clj's is not finite;
;;                the grid is even and spans the ranges
;;   projection   every drawn vertex is where this script's own
;;                orthographic map puts it -- turned by the azimuth about
;;                z, tilted by the elevation, then the scene's scale and
;;                origin -- within 0.006 px (the SVG keeps 2 decimals);
;;                the box's 8 corners lie inside the picture
;;   painter      the cells are drawn far to near: each cell's mean depth
;;                toward the viewer never decreases in drawing order
;;   holes        the cells drawn are exactly those whose four corners
;;                clj finds finite and inside the z range -- a hole or a
;;                cut pole draws nothing, and nothing else is missing
;;   ranges       the x, y and z ranges a person expects, written below
;;   svg          well-formed XML; the theme's ground
;;
;; Usage: bb test/surface_check.bb    Env: CLOTRAN (default ../clotran/clotran)
(require '[babashka.fs :as fs]
         '[babashka.process :as p]
         '[clojure.data.xml :as xml]
         '[clojure.edn :as edn]
         '[clojure.string :as str])

(def here (fs/parent (fs/absolutize *file*)))
(def root (str (fs/normalize (fs/path here ".."))))
(def path (str root "/src" java.io.File/pathSeparator root "/resources"))
(def out (str (fs/path here "out" "surface")))
(def clotran (or (System/getenv "CLOTRAN")
                 (str (fs/normalize (fs/path here ".." ".." "clotran" "clotran")))))

;; name, f, options, expected [x-range y-range z-range], holes expected?
;;   paraboloid  x^2 + y^2 on -3..3: 0 to 18, rounded out to ticks of 5: [0 20]
;;   ripple      sin r, no ranges given and not flat: -10..10; z [-1 1]
;;   saddle      x^2 - y^2 on -3..3: -9..9, ticks of 5: [-10 10]
;;   double-well (x^2-1)^2 + y^2 on -1.8..1.8 by -1.5..1.5: 0 at (+-1, 0),
;;               7.27 at the corners, ticks of 2: [0 8]
;;   gaussian    exp(-(x^2+y^2)), no ranges: flat beyond r = 2.2, more
;;               than 80% of -10..10 square, so -3..3; z 0 to 1: [0 1]
;;   hemisphere  sqrt(4 - x^2 - y^2) on -3..3: NaN beyond r = 2, holes;
;;               z 0..2: [0 2]
;;   pole        1/(x^2+y^2) on -3..3: the top 2.5% of the grid is
;;               r < 0.535, where it passes 3.5, so the top is cut there
;;               and rounded out: [0 4]; cells above are left out
;;   heat        the double well as a heat map: same ranges
(def cases
  [["paraboloid" "(fn [x y] (+ (* x x) (* y y)))" ":x [-3 3] :y [-3 3]" [[-3.0 3.0] [-3.0 3.0] [0.0 20.0]] false]
   ["ripple" "(fn [x y] (m/sin (m/sqrt (+ (* x x) (* y y)))))" "" [[-10.0 10.0] [-10.0 10.0] [-1.0 1.0]] false]
   ["saddle" "(fn [x y] (- (* x x) (* y y)))" ":x [-3 3] :y [-3 3]" [[-3.0 3.0] [-3.0 3.0] [-10.0 10.0]] false]
   ["double-well" "(fn [x y] (+ (m/pow (- (* x x) 1) 2) (* y y)))" ":x [-1.8 1.8] :y [-1.5 1.5]" [[-1.8 1.8] [-1.5 1.5] [0.0 8.0]] false]
   ["gaussian" "(fn [x y] (m/exp (- (+ (* x x) (* y y)))))" "" [[-3.0 3.0] [-3.0 3.0] [0.0 1.0]] false]
   ["hemisphere" "(fn [x y] (m/sqrt (- 4 (* x x) (* y y))))" ":x [-3 3] :y [-3 3]" [[-3.0 3.0] [-3.0 3.0] [0.0 2.0]] true]
   ["pole" "(fn [x y] (/ 1.0 (+ (* x x) (* y y))))" ":x [-3 3] :y [-3 3]" [[-3.0 3.0] [-3.0 3.0] [0.0 4.0]] true]
   ["heat" "(fn [x y] (+ (m/pow (- (* x x) 1) 2) (* y y)))" ":x [-1.8 1.8] :y [-1.5 1.5] :view :heat" [[-1.8 1.8] [-1.5 1.5] [0.0 8.0]] false]
   ["ripple-light" "(fn [x y] (m/sin (m/sqrt (+ (* x x) (* y y)))))" ":theme :light" [[-10.0 10.0] [-10.0 10.0] [-1.0 1.0]] false]])

(defn sh [cmd & {:keys [env]}]
  (let [proc (p/process cmd {:out :string :err :string :in "" :extra-env env})
        r (deref proc 600000 ::timeout)]
    (when (= r ::timeout)
      (p/destroy-tree proc) (println "deadline hit:" (first cmd)) (System/exit 1))
    (when-not (zero? (:exit r))
      (println (first cmd) "failed:" (:out r) (:err r)) (System/exit 1))
    (:out r)))

(def fns-src
  (str "(require '[clojure.math :as m])\n(def fns {"
       (str/join "\n " (map (fn [[k f]] (str (pr-str k) " " f)) cases)) "})\n"))

(fs/create-dirs out)
(def tmp (fs/create-temp-dir {:prefix "surface-check"}))

(def scenes
  (let [f (str (fs/path tmp "surfaces.cljf"))]
    (spit f (str fns-src "(require '[clotran.plot :as plot])\n"
                 (str/join (for [[k _ opts] cases
                                 :let [o (str/replace opts #":theme :\w+" "")]]
                             (str "(prn [" (pr-str k) " (plot/surface (fns " (pr-str k) ") " o ")])\n"
                                  "(plot/surface-svg (fns " (pr-str k) ") " (pr-str (str out "/" k ".svg")) " " opts ")\n")))))
    (into {} (map edn/read-string (filter #(str/starts-with? % "[") (str/split-lines (sh [clotran f] :env {"CLOTRAN_PATH" path})))))))

(def truth
  (let [in (str (fs/path tmp "grid.edn")) f (str (fs/path tmp "truth.clj"))]
    (spit in (pr-str (into {} (for [[k] cases] [k (select-keys (scenes k) [:xs :ys])]))))
    (spit f (str fns-src
                 "(prn (into {} (for [[k {:keys [xs ys]}] (read-string (slurp " (pr-str in) "))]\n"
                 "  [k (mapv (fn [x] (mapv #(let [z ((fns k) x %)] (when (Double/isFinite z) z)) ys)) xs)])))\n"))
    (edn/read-string (sh ["clojure" "-M" f]))))

(defn rad [d] (* d (/ Math/PI 180)))

(defn view-map
  "This script's own projection of data point [x y z] for scene s:
  [px py depth]."
  [{:keys [x-range y-range z-range view]}]
  (let [{:keys [azimuth elevation height scale origin]} view
        ca (Math/cos (rad azimuth)) sa (Math/sin (rad azimuth))
        ce (Math/cos (rad elevation)) se (Math/sin (rad elevation))
        unit (fn [t [a b]] (- (/ (- t a) (- b a)) 0.5))]
    (fn [[x y z] & [unit?]]
      (let [[u v w] (if unit? [x y z] [(unit x x-range) (unit y y-range) (unit z z-range)])
            X (- (* u ca) (* v sa)) Y (+ (* u sa) (* v ca)) W (* height w)]
        [(+ (origin 0) (* scale X))
         (- (origin 1) (* scale (+ (* Y se) (* W ce))))
         (- (* W se) (* Y ce))]))))

(defn parse-svg [file]
  (let [doc (xml/parse-str (slurp file))
        els (tree-seq #(seq (:content %)) #(filter map? (:content %)) doc)
        of (fn [tag] (filter #(= tag (name (:tag %))) els))
        ij #(vector (parse-long (:data-i %)) (parse-long (:data-j %)))]
    {:ground (:fill (:attrs (first (of "rect"))))
     :polys (for [e (of "polygon") :let [a (:attrs e)] :when (:data-i a)]
              {:ij (ij a) :pts (mapv #(mapv parse-double (str/split % #",")) (str/split (:points a) #" "))})
     :heat (for [e (of "rect") :let [a (:attrs e)] :when (:data-i a)] (ij a))}))

(defn check [[k _ opts [xr yr zr] holes?]]
  (let [s (scenes k) t (truth k)
        {:keys [xs ys zs z-range]} s
        n (dec (count xs))
        svg (parse-svg (str out "/" k ".svg"))
        heat? (= :heat (:kind s))
        [z0 z1] z-range
        off (for [i (range (inc n)) j (range (inc n))
                  :let [a ((zs i) j) b ((t i) j)]
                  :when (not (if (and a b) (or (= a b) (<= (abs (- a b)) (* 1e-12 (abs b)))) (= a b)))]
              [i j a b])
        even? (and (= (first xs) (xr 0)) (= (peek xs) (xr 1)) (= (first ys) (yr 0)) (= (peek ys) (yr 1))
                   (every? #(< (abs (- % (/ (- (xr 1) (xr 0)) n))) 1e-12) (map - (rest xs) xs)))
        ok? (fn [i j] (when-let [z ((t i) j)] (<= z0 z z1)))
        want (set (for [i (range n) j (range n)
                        :when (and (ok? i j) (ok? (inc i) j) (ok? (inc i) (inc j)) (ok? i (inc j)))]
                    [i j]))
        drawn (if heat? (:heat svg) (map :ij (:polys svg)))
        P (when-not heat? (view-map s))
        corner (fn [[i j]] [(xs i) (ys j) ((zs i) j)])
        quad (fn [[i j]] (map corner [[i j] [(inc i) j] [(inc i) (inc j)] [i (inc j)]]))
        far (when P (for [{:keys [ij pts]} (:polys svg)
                          [[px py] q] (map vector pts (map P (quad ij)))
                          :let [d (max (abs (- px (q 0))) (abs (- py (q 1))))]
                          :when (> d 0.006)]
                      [ij d]))
        [W H] (:size s)
        outside (when P (for [u [-0.5 0.5] v [-0.5 0.5] w [-0.5 0.5]
                              :let [[x y] (P [u v w] true)]
                              :when (not (and (<= 0 x W) (<= 0 y H)))]
                          [u v w x y]))
        depths (when P (map (fn [{:keys [ij]}] (/ (reduce + (map #((P %) 2) (quad ij))) 4)) (:polys svg)))
        unordered (when P (count (filter (fn [[a b]] (< b a)) (partition 2 1 depths))))
        fails (cond-> []
                (seq off) (conj [:on-function (count off) (first off)])
                (not even?) (conj :grid)
                (seq far) (conj [:projection (count far) (first far)])
                (seq outside) (conj [:corners outside])
                (and unordered (pos? unordered)) (conj [:painter unordered])
                (not= want (set drawn)) (conj [:holes :drawn (count (set drawn)) :want (count want)])
                (not= (count drawn) (count (set drawn))) (conj :cell-twice)
                (not= holes? (< (count want) (* n n))) (conj [:holes-expected holes? (- (* n n) (count want))])
                (not= [xr yr zr] [(:x-range s) (:y-range s) z-range]) (conj [:ranges (:x-range s) (:y-range s) z-range])
                (not= (if (str/includes? opts ":light") "white" "#0f0f12") (:ground svg)) (conj [:ground (:ground svg)]))]
    (println (format "%-12s %s, %4d of %d cells drawn, z %s: %s" k (name (:kind s)) (count drawn) (* n n) z-range
                     (if (seq fails) (str "FAIL " fails) "ok")))
    (empty? fails)))

(let [results (mapv check cases)]
  (fs/delete-tree tmp)
  (println (format "%d of %d surfaces pass every check" (count (filter true? results)) (count results)))
  (when-not (every? true? results) (System/exit 1)))
