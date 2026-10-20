# clotran-plot

Graphs for [clotran](https://github.com/clotran/clotran) -- Clojure
hosted on Fortran -- that just do the thing, as a TI-85 did: give it a
function or your data and it draws the obvious picture, with sensible
ticks and ranges, in a window
([clotran-ui](https://github.com/clotran/clotran-ui)) or to an SVG
file.

    (plot/plot (fn [x] (* x x)))   ; a parabola, ticks and range chosen for you
    (plot/plot scan-atom)          ; redrawn as the calculation fills it in

(A list of numbers graphed as a line, `(graph energies)`, is to come:
[PLAN.md](PLAN.md).)

What works today: ticks; a function's graph, or several, in a window
with trace and zoom, and to SVG; a graph of an atom, redrawn when it
changes; a graphing calculator; surfaces to SVG and, in a window, in 3D
on the GPU, turned with the mouse -- on macOS and Windows. What it is to
become, and in what order, is [PLAN.md](PLAN.md).

## In a window

Add this line to your project's `deps.edn` (`clotran new my-project` makes
one), run `clotran deps` to fetch it, start `clotran` there, and a plot
opens its own window:

    {:deps {clotran/clotran-plot {:git/url "https://github.com/clotran/clotran-plot"
                                  :git/sha "fc0e70a9e8610af85906a0eb23d4d27451596798"}}}

    (require '[clotran.plot :as plot] '[clojure.math :as m])
    (plot/plot (fn [x] x))                   ; a window: y = x over -10..10
    (plot/plot (fn [x] (* x x)))             ; the same window, now a parabola
    (plot/plot m/sin)                        ; any function of x
    (plot/plot [m/sin m/cos #(/ % 4)])       ; three at once, one colour each
    (def f (atom m/sin))
    (plot/plot f)  (reset! f m/cos)          ; redrawn when the atom changes

Each graph draws in the one graph window, brought to the front; close
it, and the next opens a new one. Something that is not a function of
x -- `(plot/plot #(%))`, which calls x -- is refused before a window
opens, saying why.

Ticks are data: `:x-ticks` and `:y-ticks` take the values to mark, made
however you like -- by `clotran.plot.ticks` (below) or by hand -- and
are kept through zooms; without them the graph chooses its own:

    (require '[clotran.plot.ticks :as t])
    (plot/plot m/sin :y-ticks [-1 0 1])
    (plot/plot m/sin :x-ticks (t/extended -10 10 10))

As a TI-85: move the mouse and the nearest curve's point is marked with
its x and y (TRACE); drag a box to zoom into it, `+` and `-` zoom in and
out about the middle, Escape or a double-click goes back, `0` is
-10..10 again (ZOOM). The window redraws for its size. It is the same
picture the SVG is written from (`clotran.plot.marks`), drawn by
[clotran-ui](https://github.com/clotran/clotran-ui).

**The graphing calculator** -- a text field and a graph; type an
expression in x and the graph follows as you type -- in the project
directory, after `clotran deps`:

    clotran -m clotran.plot.calc

`(* x x)`, `(sin x)`, several at once as `(sin x) (/ x 4)`; sin, cos,
tan, sqrt, exp, log, pow, PI and the rest of `clojure.math` by name.
While what is typed is not finished, the last graph stays and the line
under the field says why.

**Surfaces in 3D**, drawn by the GPU (Metal on macOS, Vulkan on Windows);
drag to turn, scroll to zoom:

    (plot/plot3d (fn [x y] (m/sin (m/sqrt (+ (* x x) (* y y))))))

The mesh (`clotran.plot.mesh`) is the SVG surface's grid, its colours from
the theme's colormap, its triangles lit as the SVG shades its cells. No
tick labels in the 3D view yet.

This needs clotran 1.0.0-rc.8 or later. On macOS (arm64) and Windows
no compiler and no git are needed: where there is no C compiler,
clotran-ui fetches its native library prebuilt, and `clotran deps`
fetches a library as GitHub's archive of its sha. On Linux, clotran-ui
builds its library with the system's C compiler and GTK 3 (not yet
tested there).

`test/window_check.bb` opens real windows, drives them with events as a
person would, and checks 18 things: the window, its pixels, trace, zoom,
`+`, going back, resizing, a live atom, three curves, drawing again in the one window, refusing what is not a function of x, the calculator, the
3D view and turning it, that what each window shows has exactly its own
curves (by their colours) at its own size, and that every window closes and the program
ends by itself. On Windows, test/window_check.cljf runs on the box and
`bb test/window_check.bb --from DIR` judges what it wrote: 15 of 15 there -- the
check of what windows show is skipped, since the box's session has no
display and its windows' own pictures are black.


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
Its look is a theme (see Themes below). The default, `:dark`, is for
screens and is inspired by Manim's look: a near-black ground, axes
through the origin with arrow heads, a faint blue grid, Manim's colours
in turn, and serif labels with true minus signs. `:light` has a white
ground and a frame, for papers.

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

## Surfaces

    (plot/surface-svg (fn [x y] (+ (* x x) (* y y))) "bowl.svg" :x [-3 3] :y [-3 3])
    (plot/surface-svg (fn [x y] (m/sin (m/sqrt (+ (* x x) (* y y))))) "ripple.svg")
    (plot/surface-svg pes "pes.svg" :view :heat)      ; seen from above
    (plot/surface f :azimuth -60 :elevation 40)        ; the scene, as data

`surface` takes a function of two doubles, z = f(x, y), and samples it
on a grid of 40 by 40 cells (`:n`). Without ranges, x and y run over
-10..10, or over -3..3 when the surface is flat on most of that square,
as a Gaussian bump is. The z range is the whole surface's, unless one
side runs off to a pole; that side is then cut at the middle 95%, as
for curves, and the range is rounded out to its ticks. Where f is not
finite there is a hole. A cell with any corner in a hole or outside the
z range is left out.

The view is orthographic: parallel lines stay parallel, and a tick step
looks the same anywhere along its axis. Mathematica and gnuplot also
draw surfaces this way by default. The cells are drawn back to front
(the painter's algorithm). Each is filled from the theme's `:colormap`
at its height, shaded by a light from the upper left, and outlined by
the mesh. A box frames the surface, with ticks on its two nearest bottom
edges and on its leftmost vertical edge. `:view :heat` draws the same
grid from above, as a heat map.

`test/surface_check.bb` checks 9 surfaces. Every grid z equals clj's
f(x, y). Every drawn vertex is where the script's own projection puts
it. The cells are drawn far to near. Exactly the cells outside holes
and the z range are drawn. The x, y and z ranges are the expected ones,
and each SVG is well-formed.

## Themes

A theme is a plain EDN map. The built-in ones are files themselves,
[`dark.edn`](resources/clotran/plot/themes/dark.edn) and
[`light.edn`](resources/clotran/plot/themes/light.edn). Make your own by
naming a theme to start from and changing only the keys you want:

    ;; paper.edn
    {:extends :light
     :background "#fbf8f1"
     :palette ["#0b3c5d" "#b22222" "#2e8b57" "#6a3d9a"]}

    (plot/graph-svg f "f.svg" :theme "paper.edn")          ; a file
    (plot/graph-svg f "f.svg" :theme {:extends :dark :palette ["#ff00ff"]})

| key             | value                                                         |
|-----------------|---------------------------------------------------------------|
| `:background`   | colour of the ground                                          |
| `:axes`         | `:frame`: a box, with ticks and labels outside it on the left and bottom; `:origin`: axes through the origin (or along the nearer edge), with arrow heads and ticks across them |
| `:ink`          | colour of the axes or frame, the ticks and the arrow heads    |
| `:axis-width`   | their stroke width, above 0                                   |
| `:zero-lines`   | colour of the lines x = 0 and y = 0, or `:none`               |
| `:grid`         | colour of a line at every tick, or `:none`                    |
| `:grid-opacity` | its opacity, 0 to 1                                           |
| `:label-colour` | colour of the tick labels                                     |
| `:font`         | their font-family, a CSS font stack                           |
| `:font-size`    | their size, above 0                                           |
| `:minus`        | the minus sign in labels: `"-"`, or `"\u2212"` as LaTeX sets it |
| `:palette`      | the curves' colours, a non-empty vector, used in turn         |
| `:stroke`       | the curves' stroke width, above 0                             |
| `:colormap`     | a surface's colours from low to high z: 2 or more `#rrggbb` stops, evenly spaced |
| `:mesh`         | colour of a surface's mesh lines, or `:none`                  |
| `:mesh-opacity` | their opacity, 0 to 1                                         |
| `:extends`      | optional: a built-in's keyword, or an .edn file's path, relative to this file |

A colour is hex (`#rgb`, `#rrggbb` or `#rrggbbaa`) or one of CSS 1's
sixteen names (`black`, `white`, `red`, ...). Every key is required,
unless the theme has `:extends`. An unknown key, a bad value, a missing
key or a missing file is an error that names the key and the file. It
never falls back to a default silently.

**Your default.** If a `plot-theme.edn` file is in the directory clotran
runs in (your project's root), it is the theme used when none is named.
This is a file, not a `deps.edn` key, because it needs no `deps.edn` at
all. `test/theme_check.bb` checks a custom theme, a palette-only
override, the project default, and 21 malformed themes, each refused
with the right message.

## License

EPL-2.0 (LICENSE).
