#!/bin/sh
# test/run.sh: every check of clotran-plot. Needs bb, clj, R with the labeling
# package, and clotran (CLOTRAN, default ../clotran/clotran).
set -e
cd "$(dirname "$0")/.."
bb test/ticks_oracle.bb
bb test/plot_check.bb dark
bb test/plot_check.bb light
bb test/theme_check.bb
bb test/surface_check.bb
# Windows on screen: macOS for now, and a clotran with fortran/main-thread*
# (core branch feat/ui-main-thread until it merges); clotran-ui beside this
# checkout (CLOTRAN_UI). Its own CLOTRAN for the windows: CLOTRAN_WINDOW.
if [ "$(uname)" = Darwin ]; then CLOTRAN="${CLOTRAN_WINDOW:-${CLOTRAN:-../clotran/clotran}}" bb test/window_check.bb; fi
