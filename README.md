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

What works today: ticks, and a function's graph to SVG (half of
milestone 2). What it is to become, and in what order, is
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
both for the ticks of 35 hand-picked ranges (with and without
`only.loose`) and 500 seeded random ones and requires the same ticks;
today they agree bit for bit.

## Graphing

    (require '[clotran.plot :as plot] '[clojure.math :as m])
    (plot/graph-svg #(* % %) "parabola.svg")          ; x from -10 to 10
    (plot/graph-svg m/tan "tan.svg" :x [-5 5])
    (plot/graph-svg [m/sin m/cos #(/ % 4)] "three.svg")   ; one colour each
    (plot/graph-svg m/exp "exp.svg" :theme :light)    ; for a paper
    (plot/graph m/sin 0 10)     ; the scene: plain data, nothing drawn

`graph` takes a function of one double, or a vector of them, and returns
a scene: the x and y ranges, their ticks, and each curve as polylines in
data units. `graph-svg` writes the scene as an SVG of 600 by 400 pixels.
It has two themes. The default, `:dark`, is for screens and is inspired
by Manim's look: a near-black ground, axes through the origin with arrow
heads, a faint blue grid, Manim's colours in turn, and serif labels with
true minus signs. `:light` has a white ground and a frame, for papers.

The curve is sampled as Mathematica's Plot samples one
(`clotran.plot.sample`): 50 even points, then intervals halved, up to 6
times, wherever the line bends by more than 5 degrees on screen. The line is broken at poles and
jumps, not joined across them. The y range is the whole curve's unless
it runs off to a pole; then it is the middle 95% of the curve, so 1/x
shows -2..2 and not -200..200. Either way it is rounded out to its
ticks.

`test/plot_check.bb` checks x^2, x^3, sin, 1/x, e^x, tan, floor, and sin,
cos and x/4 together, in each theme (`test/run.sh` runs both). Every
sample point lies on the function, as clj computes it. No line crosses a
pole or a jump. The curve breaks into exactly as many pieces as the
function has. At 3000 random x the drawn line is within a pixel of the
true curve. The y ranges are the expected ones, and each SVG is
well-formed, with the theme's colours, ground, axes and minus signs.
The example SVGs land in `test/out/dark/` and `test/out/light/`.

## License

EPL-2.0 (LICENSE).
