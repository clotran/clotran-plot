# clotran-plot

Graphs for [clotran](https://github.com/clotran/clotran) -- Clojure
hosted on Fortran -- that just do the thing, as a TI-85 did: give it a
function or your data and it draws the obvious picture, with sensible
ticks and ranges, in a window
([clotran-ui](https://github.com/clotran/clotran-ui)) or to an SVG or PNG
file.

    (graph (* x x))          ; a parabola, ticks and range chosen for you
    (graph energies)         ; a list of numbers: a line over the step
    (graph scan-atom)        ; redrawn as the calculation fills it in

Only the ticks work yet (milestone 1); what it is to become, and in what
order, is
[PLAN.md](PLAN.md).

## Ticks

`clotran.plot.ticks` chooses the tick values for an axis by the extended
Wilkinson algorithm (Talbot, Lin and Hanrahan, 2010), ported from R's
`labeling::extended`, R's reference implementation:

    (require '[clotran.plot.ticks :as t])
    (t/ticks -3.7 12.2)          ;=> [-4.0 0.0 4.0 8.0 12.0]
    (t/extended 0 1 5)           ;=> [0.0 0.25 0.5 0.75 1.0]  about m = 5 ticks
    (t/extended 0 1 5 {:only-loose true})   ; ticks that cover the data

R is the oracle: `test/run.sh` (bb, R with `labeling`, and clotran) asks
both for the ticks of 35 hand-picked ranges and 500 seeded random ones
and requires the same ticks; today they agree bit for bit.

## License

EPL-2.0 (LICENSE).
