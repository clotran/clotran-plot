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

Nothing works yet: what it is to become, and in what order, is
[PLAN.md](PLAN.md).

## License

EPL-2.0 (LICENSE).
