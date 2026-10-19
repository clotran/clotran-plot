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
;;   drawn     what each window SHOWS (its own picture of itself) has a curve
;;             colour exactly when its scene has that curve: sin, the three,
;;             the calculator's (* x x) and (sin x) (/ x 4)
;;   sizes     every scene was made at its drawing area's size
;;   gpu       3D on the GPU (Metal; Vulkan on Windows): the camera starts at
;;             clotran.plot.surface's view (-35, 28, zoom 1); the offscreen
;;             picture has the dark ground at its corner and the surface
;;             over a fifth of it at least; the frame as shown is read back
;;   turned    a drag of (+80, -40) px turns it half a degree a pixel, to
;;             (-75, 8), a scroll of -5 zooms it by e^0.25, and the picture
;;             changes
;;
;; On macOS this runs clotran itself. A run elsewhere (Windows: the
;; same window_check.cljf, its out/ fetched) is judged with --from DIR,
;; its pictures BMPs; sips reads both.
;; Usage: bb test/window_check.bb [--from DIR]
;; Env: CLOTRAN, CLOTRAN_UI (../clotran-ui)
(require '[babashka.fs :as fs]
         '[babashka.process :as p]
         '[clojure.edn :as edn]
         '[clojure.string :as str])

(def here (fs/parent (fs/absolutize *file*)))
(def root (fs/normalize (fs/path here "..")))
(def ui-root (or (System/getenv "CLOTRAN_UI") (str (fs/normalize (fs/path root ".." "clotran-ui")))))
(def clotran (or (System/getenv "CLOTRAN") (str (fs/normalize (fs/path root ".." "clotran" "clotran")))))
(def from (second (drop-while #(not= "--from" %) *command-line-args*)))
(def out (or from (str (fs/path here "out" "window"))))
(fs/create-dirs out)

(def run
  (if from
    {:exit 0 :err ""}
    (let [proc (p/process [clotran (str (fs/path here "window_check.cljf")) out]
                          {:out :string :err :string :in ""
                           :extra-env {"CLOTRAN_PATH" (str/join ":" [(str ui-root "/src") (str root "/src") (str root "/resources")])}})
          r (deref proc 600000 ::timeout)]
      (when (= r ::timeout) (p/destroy-tree proc) (println "deadline hit") (System/exit 1))
      r)))

(def res (into {} (keep #(when (str/starts-with? % "[") (let [v (edn/read-string %)] [(first v) (vec (rest v))]))
                        (str/split-lines (slurp (str out "/results.edn"))))))

(defn pic
  "A picture of the run: name.png, or name.bmp from Windows."
  [name] (let [p (str out "/" name ".png")] (if (fs/exists? p) p (str out "/" name ".bmp"))))

;; ---- pixels: any picture to BMP by sips, read here ----
(defn bmp [f]
  (let [b (str f ".read.bmp")]
    (p/shell {:out :string :err :string} "sips" "-s" "format" "bmp" f "--out" b)
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
      {:keys [rgb]} (bmp (pic "snapshot-sin"))
      [bx by bw bh] box
      px (fn [x] (+ bx (* (- x (xr 0)) (/ bw (- (xr 1) (xr 0))))))
      py (fn [y] (- (+ by bh) (* (- y (yr 0)) (/ bh (- (yr 1) (yr 0))))))
      cx (Math/round (double (px (/ Math/PI 2)))) cy (Math/round (double (py 1.0)))
      near (for [dx (range -2 3) dy (range -2 3)] (rgb (+ cx dx) (+ cy dy)))]
  (check "pixels" (and ok (close-to? (rgb 3 3) [0x0f 0x0f 0x12] 2) (some #(close-to? % [0x58 0xC4 0xDD] 40) near))
         [(rgb 3 3) cx cy]))

(let [[ok] (res :capture) f (pic "window-sin")
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
(check "reused" (= (res :reused) [true 1 1 [0.0 50.0 100.0]]) (res :reused))
(check "refused" (= (res :refused) [true 1]) (res :refused))
(check "calc" (and (= (res :calc-square) [[0.0 100.0] true])
                   (= (res :calc-unfinished) [[0.0 100.0] true])
                   (= (res :calc-two) [2 true]))
       [(res :calc-square) (res :calc-unfinished) (res :calc-two)])
(check "closed"(and (= (res :closed) [0]) (zero? (:exit run))) [(res :closed) (:exit run)])

(let [[cam ok-snap ok-cap] (res :gpu-view)
      {:keys [rgb w h]} (when ok-snap (bmp (pic "gpu-ripple")))
      lit (when rgb (count (for [x (range 0 w 10) y (range 0 h 10) :when (not (close-to? (rgb x y) [0x0f 0x0f 0x12] 6))] 1)))
      total (when rgb (* (count (range 0 w 10)) (count (range 0 h 10))))]
  (check "gpu" (and (= cam {:azimuth -35.0 :elevation 28.0 :zoom 1.0}) ok-snap ok-cap (fs/exists? (pic "gpu-shown"))
                    (close-to? (rgb 2 2) [0x0f 0x0f 0x12] 2) (> lit (/ total 5)))
         [cam lit total]))

(let [[cam ok] (res :gpu-turned)
      a (when ok (bmp (pic "gpu-ripple"))) b (when ok (bmp (pic "gpu-ripple-turned")))
      differ (when ok (count (for [x (range 0 700 10) y (range 0 560 10)
                                   :when (not (close-to? ((:rgb a) x y) ((:rgb b) x y) 8))] 1)))]
  (check "turned" (and ok (= -75.0 (:azimuth cam)) (= 8.0 (:elevation cam))
                       (< (abs (- (:zoom cam) (Math/exp 0.25))) 1e-4) (> differ 100))
         [cam differ]))

;; what the window SHOWS against its scene: each curve colour of the dark
;; palette is in the window's own picture exactly when the scene has that
;; curve (a window that replayed another's picture showed a curve nobody
;; asked for), and every scene was made at its area's size (one that was
;; not was drawn clipped)
(def palette [[0x58 0xC4 0xDD] [0xFF 0xFF 0x00] [0x83 0xC1 0x67]])
(defn colour-counts
  "Pixels near each palette colour in the picture, or nil when it is black
  throughout -- a window in a session with no display draws nothing."
  [f]
  (let [{:keys [rgb w h]} (bmp f)
        px (for [x (range w) y (range h)] (rgb x y))]
    (when (some #(> (apply max %) 24) px)
      (mapv (fn [c] (count (filter #(close-to? % c 30) px))) palette))))

(def drawn-cases [["window-sin" [true false false]] ["window-three" [true true true]]
                  ["calc-square" [true false false]] ["calc-two" [true true false]]])
(def drawn (for [[name want] drawn-cases] [name want (colour-counts (pic name))]))
(if (every? (fn [[_ _ n]] (nil? n)) drawn)
  (println "drawn    skipped: every window picture is black (no display in this session); not counted")
  (check "drawn" (every? (fn [[_ want n]] (and n (every? true? (map (fn [w k] (if w (>= k 40) (< k 5))) want n)))) drawn)
         (mapv (fn [[name _ n]] [name n]) drawn)))

(check "sizes" (every? (fn [k] (let [[scene area] (res k)] (= scene (mapv long area))))
                       [:sizes-sin :sizes-three :sizes-calc])
       (mapv res [:sizes-sin :sizes-three :sizes-calc]))

(doseq [[n ok d] @checks] (println (format "%-8s %s %s" n (if ok "ok" "FAILED") (pr-str d))))
(let [good (count (filter second @checks))]
  (println (str good " of " (count @checks) " window checks pass"))
  (when (not= good (count @checks))
    (println (:err run))
    (System/exit 1)))
