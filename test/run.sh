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
