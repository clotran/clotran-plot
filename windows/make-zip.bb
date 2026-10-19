;; windows/make-zip.bb UI-SHA PLOT-SHA OUT.zip: graphing-calculator.zip --
;; clotran-ui and clotran-plot at those commits, side by side, and a
;; "Graphing Calculator.cmd" on top to double-click. For a Windows machine
;; with clotran installed and nothing else: no git, no compiler.
(require '[babashka.fs :as fs] '[babashka.process :as p])
(let [[ui plot out] *command-line-args*
      here (fs/parent (fs/absolutize *file*))
      plot-repo (str (fs/parent here))
      ui-repo (str (fs/normalize (fs/path plot-repo ".." "clotran-ui")))
      tmp (str (fs/create-temp-dir))
      top (str tmp "/graphing-calculator")]
  (when-not (and ui plot out) (println "usage: bb windows/make-zip.bb UI-SHA PLOT-SHA OUT.zip") (System/exit 1))
  (fs/create-dirs top)
  (doseq [[repo sha dir] [[ui-repo ui "clotran-ui"] [plot-repo plot "clotran-plot"]]]
    (p/shell {:dir repo :extra-env {"COPYFILE_DISABLE" "1"}}
             "git" "archive" "--format=tar" (str "--prefix=" dir "/") "-o" (str tmp "/" dir ".tar") sha)
    (p/shell {:extra-env {"COPYFILE_DISABLE" "1"}} "tar" "xf" (str tmp "/" dir ".tar") "-C" top))
  (spit (str top "/Graphing Calculator.cmd")
        (str "@echo off\r\nrem the graphing calculator: double-click me\r\n"
             "call \"%~dp0clotran-plot\\windows\\Graphing Calculator.cmd\" %*\r\n"))
  (fs/delete-if-exists out)
  (p/shell {:dir tmp :extra-env {"COPYFILE_DISABLE" "1"}} "zip" "-qr" (str (fs/absolutize out)) "graphing-calculator")
  (println "wrote" out))
