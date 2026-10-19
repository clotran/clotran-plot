#!/usr/bin/env bb
;; test/ticks_oracle.bb: clotran.plot.ticks/extended against R's
;; labeling::extended, the oracle, over hand-picked ranges and a seeded
;; random set. Each range is asked of R and of clotran (interpreted, with
;; CLOTRAN_PATH at this repo's src); they must give the same number of
;; ticks, each equal within 1e-9 of the largest tick's magnitude.
;;
;; Why a tolerance at all: the port does R's arithmetic in R's order, so
;; the ticks are normally the same bits. What may differ is the last bit
;; of a library call -- log10 and pow come from R's libm on one side and
;; gfortran's runtime on the other, and R's %% uses long double where the
;; platform has one (x86-64) -- and an ulp moved there moves a tick by an
;; ulp, never by 1e-9 of the axis. A wrong tick choice is off by a whole
;; step. The bit-exact count is reported beside the verdict (on macOS
;; arm64, 2026-10-18: all of them).
;;
;; R is sent the ranges as hex floats: its decimal reader is not
;; correctly rounded (1.0E-300 reads as 9.999999999999994E-301, the
;; largest double as Inf), and a range R misreads is not the range asked.
;;
;; Usage: bb test/ticks_oracle.bb [N-random]   (default 500; seed 2010)
;; Env: CLOTRAN (default ../clotran/clotran), RSCRIPT (default Rscript).
(require '[babashka.fs :as fs]
         '[babashka.process :as p]
         '[clojure.string :as str])

(def here (fs/parent (fs/absolutize *file*)))
(def src (str (fs/normalize (fs/path here ".." "src"))))
(def clotran (or (System/getenv "CLOTRAN")
                 (str (fs/normalize (fs/path here ".." ".." "clotran" "clotran")))))
(def rscript (or (System/getenv "RSCRIPT") "Rscript"))

(def fixed
  [[0 1 5] [0 0 5] [5 5 5] [-3 -3 4] [1 0 5] [0 100 5] [0 10 10]
   [-3.7 12.2 5] [-7.3 -2.1 5] [-0.5 1234 5] [0.1 0.7 7] [1e-5 3e-5 5]
   [1e-9 1.000001e-9 5] [-1e-10 1e-10 5] [1e-15 9e-15 5] [1e-13 2e-13 5]
   [1e9 1000000001 5] [123456789 123456790 3] [1e15 1.0001e15 5]
   [1e100 1.0000001e100 5] [-1e150 1e150 5] [1e153 1e154 5]
   [1e-300 1e300 5] [-1e308 1e308 5] [-1.7976931348623157E308 1.7976931348623157E308 4]
   [0 1 2] [0 1 3] [0 1 20] [-1 1 50] [3.14159 2.71828 5] [-273.15 100 6]
   [0.001 1000 5] [6.02e23 6.03e23 5] [-1.6e-19 1.6e-19 5] [99.99 100.01 5]])

(defn random-cases [n]
  (let [r (java.util.Random. 2010)
        u #(.nextDouble r)]
    (vec (for [_ (range n)]
           (let [width (* (Math/pow 10 (- (.nextInt r 31) 15)) (+ 0.01 (u)))
                 lo (case (.nextInt r 3)
                      0 (- (* (u) width))
                      1 (* width (Math/pow 10 (* 6 (u))))
                      2 (- (* width (Math/pow 10 (* 6 (u))))))
                 [a b] (if (zero? (.nextInt r 8)) [(+ lo width) lo] [lo (+ lo width)])]
             [(double a) (double b) (+ 2 (.nextInt r 11))])))))

(defn sh [cmd ms]
  (let [proc (p/process cmd {:out :string :err :string :in ""
                             :extra-env {"CLOTRAN_PATH" src}})
        r (deref proc ms ::timeout)]
    (when (= r ::timeout)
      (p/destroy-tree proc)
      (println "deadline hit:" (first cmd)) (System/exit 1))
    (when-not (zero? (:exit r))
      (println (first cmd) "failed:" (:err r)) (System/exit 1))
    (:out r)))

(defn parse [out n who]
  (let [lines (str/split-lines out)
        lines (filterv #(str/starts-with? % "T") lines)]
    (when-not (= n (count lines))
      (println who "gave" (count lines) "results for" n "ranges") (System/exit 1))
    (mapv #(mapv parse-double (rest (str/split (str/trim %) #"\s+"))) lines)))

(let [cases (into fixed (random-cases (parse-long (or (first *command-line-args*) "500"))))
      n (count cases)
      dir (fs/create-temp-dir {:prefix "ticks-oracle"})
      rfile (str (fs/path dir "cases.txt"))
      cfile (str (fs/path dir "ticks.cljf"))
      _ (spit rfile (str/join (map (fn [[a b m]] (str (Double/toHexString (double a)) " " (Double/toHexString (double b)) " " m "\n")) cases)))
      _ (spit cfile (str "(require '[clotran.plot.ticks :as t])\n"
                         "(doseq [[a b m] '" (pr-str (mapv (fn [[a b m]] [(double a) (double b) m]) cases)) "]\n"
                         "  (println (apply str \"T\" (map #(str \" \" (pr-str %)) (t/extended a b m)))))\n"))
      rout (sh [rscript "-e" (str "library(labeling); d <- read.table('" rfile "');"
                                    "for (i in seq_len(nrow(d))) cat('T', sprintf('%.17g', extended(d[i,1], d[i,2], d[i,3])), '\\n')")]
                 600000)
      cout (sh [clotran cfile] 600000)
      rs (parse rout n "R")
      cs (parse cout n "clotran")
      verdicts (map (fn [r cl]
                      (cond (not= (count r) (count cl)) :count
                            (= r cl) :exact
                            (every? true? (map (fn [x y] (<= (abs (- x y)) (* 1e-9 (apply max (map abs r)))))
                                               r cl)) :ulps
                            :else :value))
                    rs cs)
      bad (remove (comp #{:exact :ulps} first) (map vector verdicts cases rs cs))
      freq (frequencies verdicts)]
  (fs/delete-tree dir)
  (println (format "%d of %d ranges agree with R (%d bit-exact, %d within 1e-9)"
                   (+ (freq :exact 0) (freq :ulps 0)) n (freq :exact 0) (freq :ulps 0)))
  (when (seq bad)
    (println "mismatches by kind:" (dissoc freq :exact :ulps))
    (doseq [[k c r cl] (take 5 bad)]
      (println " " k c "\n    R:      " r "\n    clotran:" cl))
    (System/exit 1)))
