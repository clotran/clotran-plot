#!/usr/bin/env bb
;; test/window_check.bb: graphs in real windows (clotran.plot/show over
;; clotran-ui), driven as a person would -- the mouse and keys put into the
;; window's event queue (clotran.ui/inject!) -- and judged here:
;;
;;   opened    a window opens, and one is open
;;   pixels    its drawing, replayed offscreen (clotran.ui/snapshot): the
;;             dark theme's ground at a corner, and the curve's colour
;;             within 2 px of (pi/2, 1) where sin peaks
;;   capture   the window as it is on screen, frame and title bar too,
;;             read back from the window itself: a PNG at least 600x400
;;   trace     the mouse over x = 1.3 marks the curve's point: x within
;;             one pixel's width of 1.3, y = sin x within 1e-12
;;   zoom      a box dragged from (-2, 0.5) to (3, -0.5) is the new range,
;;             within a pixel
;;   plus      + halves both ranges about the middle
;;   back      Escape twice: back to -10..10, -1..1
;;   resize    an 800x500 window draws an 800x500 graph, margins kept
;;   live      a graph of an atom: sin, then cos once it is reset!
;;   several   three functions, three curves
;;   calc      the calculator: typing (* x x) graphs it (y 0..100); an unfinished
;;             "(* x" keeps the last graph; two forms are two curves
;;   closed    no window left, and the program ends by itself (status 0)
;;
;; macOS only for now (the pictures need its offscreen path and sips).
;; Usage: bb test/window_check.bb   Env: CLOTRAN, CLOTRAN_UI (../clotran-ui)
(require '[babashka.fs :as fs]
         '[babashka.process :as p]
         '[clojure.edn :as edn]
         '[clojure.string :as str])

(def here (fs/parent (fs/absolutize *file*)))
(def root (fs/normalize (fs/path here "..")))
(def ui-root (or (System/getenv "CLOTRAN_UI") (str (fs/normalize (fs/path root ".." "clotran-ui")))))
(def clotran (or (System/getenv "CLOTRAN") (str (fs/normalize (fs/path root ".." "clotran" "clotran")))))
(def out (str (fs/path here "out" "window")))
(fs/create-dirs out)

(def run
  (let [proc (p/process [clotran (str (fs/path here "window_check.cljf")) out]
                        {:out :string :err :string :in ""
                         :extra-env {"CLOTRAN_PATH" (str/join ":" [(str ui-root "/src") (str root "/src") (str root "/resources")])}})
        r (deref proc 600000 ::timeout)]
    (when (= r ::timeout) (p/destroy-tree proc) (println "deadline hit") (System/exit 1))
    r))

(def res (into {} (keep #(when (str/starts-with? % "[") (let [v (edn/read-string %)] [(first v) (vec (rest v))]))
                        (str/split-lines (:out run)))))

;; ---- pixels: PNG to BMP by sips, read here ----
(defn bmp [png]
  (let [b (str png ".bmp")]
    (p/shell {:out :string :err :string} "sips" "-s" "format" "bmp" png "--out" b)
    (let [bs (fs/read-all-bytes b)
          u (fn [i n] (reduce (fn [a k] (+ a (bit-shift-left (bit-and 0xff (aget bs (+ i k))) (* 8 k)))) 0 (range n)))
          off (u 10 4) w (u 18 4) h0 (u 22 4) h (if (> h0 0x7fffffff) (- 0x100000000 h0) h0)
          top-down (> h0 0x7fffffff) bpp (u 28 2) stride (* 4 (quot (+ (* w bpp) 31) 32))]
      {:w w :h h
       :rgb (fn [x y] (let [row (if top-down y (- h 1 y)) i (+ off (* row stride) (* x (quot bpp 8)))]
                        [(bit-and 0xff (aget bs (+ i 2))) (bit-and 0xff (aget bs (+ i 1))) (bit-and 0xff (aget bs i))]))})))

(defn close-to? [[r g b] [r2 g2 b2] tol] (every? #(<= (abs %) tol) [(- r r2) (- g g2) (- b b2)]))

(def checks (atom []))
(defn check [name ok detail] (swap! checks conj [name (boolean ok) detail]))

(let [[id n] (res :opened)] (check "opened" (and (pos? id) (= 1 n)) [id n]))

(let [[ok box xr yr] (res :snapshot)
      {:keys [rgb]} (bmp (str out "/snapshot-sin.png"))
      [bx by bw bh] box
      px (fn [x] (+ bx (* (- x (xr 0)) (/ bw (- (xr 1) (xr 0))))))
      py (fn [y] (- (+ by bh) (* (- y (yr 0)) (/ bh (- (yr 1) (yr 0))))))
      cx (Math/round (double (px (/ Math/PI 2)))) cy (Math/round (double (py 1.0)))
      near (for [dx (range -2 3) dy (range -2 3)] (rgb (+ cx dx) (+ cy dy)))]
  (check "pixels" (and ok (close-to? (rgb 3 3) [0x0f 0x0f 0x12] 2) (some #(close-to? % [0x58 0xC4 0xDD] 40) near))
         [(rgb 3 3) cx cy]))

(let [[ok] (res :capture) f (str out "/window-sin.png")
      {:keys [w h]} (when (fs/exists? f) (bmp f))]
  (check "capture" (and ok w (>= w 600) (>= h 400)) [w h]))

(let [[_ _ t] (res :trace)]
  (check "trace" (and t (< (abs (- (:x t) 1.3)) (/ 20.0 520)) (< (abs (- (:y t) (Math/sin (:x t)))) 1e-12)) t))

(let [[_ x y xr yr] (res :zoom) px (/ 20.0 520) py (/ 2.0 340)]
  (check "zoom" (and (< (abs (- (x 0) -2)) px) (< (abs (- (x 1) 3)) px)
                     (< (abs (- (y 0) -0.5)) py) (< (abs (- (y 1) 0.5)) py) (= x xr) (= y yr))
         [x y]))

(let [[xr yr] (res :plus)]
  (check "plus" (and (< (abs (- (xr 0) -0.75)) 0.05) (< (abs (- (xr 1) 1.75)) 0.05)
                     (< (abs (- (yr 0) -0.25)) 0.01) (< (abs (- (yr 1) 0.25)) 0.01)) [xr yr]))

(check "back" (= (res :back) [[-10.0 10.0] [-1.0 1.0]]) (res :back))
(check "resize" (= (res :resize) [[800 500] [60 20 720 440]]) (res :resize))

(let [[[x0 y0]] (res :live-before) [[x1 y1]] (res :live-after)]
  (check "live" (and (= x0 x1 -10.0) (< (abs (- y0 (Math/sin -10.0))) 1e-12) (< (abs (- y1 (Math/cos -10.0))) 1e-12))
         [y0 y1]))

(check "several" (= (res :three) [3 true]) (res :three))
(check "calc" (and (= (res :calc-square) [[0.0 100.0] true])
                   (= (res :calc-unfinished) [[0.0 100.0] true])
                   (= (res :calc-two) [2 true]))
       [(res :calc-square) (res :calc-unfinished) (res :calc-two)])
(check "closed"(and (= (res :closed) [0]) (zero? (:exit run))) [(res :closed) (:exit run)])

(doseq [[n ok d] @checks] (println (format "%-8s %s %s" n (if ok "ok" "FAILED") (pr-str d))))
(let [good (count (filter second @checks))]
  (println (str good " of " (count @checks) " window checks pass"))
  (when (not= good (count @checks))
    (println (:err run))
    (System/exit 1)))
