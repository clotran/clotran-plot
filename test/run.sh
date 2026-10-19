#!/bin/sh
# test/run.sh: every check of clotran-plot. Needs bb, R with the labeling
# package, and clotran (CLOTRAN, default ../clotran/clotran).
set -e
cd "$(dirname "$0")/.."
bb test/ticks_oracle.bb "$@"
