# clotran-plot: the plan

clotran's PLAN.md step 37. James, 2026-10-18: "I want something
intuitive and not some programmers idea of what a good plotting library
looks like"; "Ti-85 graphing calculators were cool because they just did
the thing"; "I think we need good plotting if we are going to be a
scientific language... show people how things are developing. like
energy stabilizations, curves etc."

## The principle: the data picks the picture

One verb, `graph`, looks at what it is given and draws the obvious thing
(Mackinlay et al., "Show Me", 2007, which picks the chart from the
data's shape):

| given                                   | drawn                              |
|-----------------------------------------|------------------------------------|
| an expression or function of x          | a smooth curve                     |
| several of them                         | each in its colour, as Y1, Y2, Y3  |
| a list of numbers                       | a line over the step (convergence) |
| timestamped values                      | a line on a calendar time axis     |
| pairs of numbers                        | a scatter                          |
| categories with numbers                 | bars (a pie when asked)            |
| a grid of values over x and y           | a 3D surface (a PES)               |
| atoms with coordinates                  | a ball-and-stick molecule          |
| an atom holding any of these            | the same, redrawn as it changes    |

Asking for another kind ("as bars", "log y") is always possible and
never necessary. In a window: hover to read a value (trace), drag a box
to zoom, double-click to go back -- the TI-85's TRACE and ZOOM.

## What makes it not dumb, and where each comes from

- **Ticks**: extended Wilkinson (Talbot, Lin, Hanrahan, "An Extension of
  Wilkinson's Algorithm for Positioning Tick Labels on Axes", 2010) --
  candidate tick sets scored for simple numbers, coverage of the data,
  density and legible labels. R's `labeling::extended` is the reference
  implementation, and so the oracle: our ticks for a range are its ticks.
- **Functions**: sampled adaptively, as Mathematica's Plot does -- even
  points first, then intervals split where the curve bends (about 5
  degrees) up to a depth; poles and jumps detected and not joined; the
  y range chosen from where most of the curve lies, so 1/x and tan x do
  not flatten the rest. Later, Tupper's interval arithmetic (GrafEq,
  SIGGRAPH 2001) for curves sampling cannot get right.
- **Time**: calendar-aware ticks -- seconds, minutes, hours, days,
  months, years by the span -- as matplotlib's AutoDateLocator and d3's
  time scales choose them.
- **Ranges** for data: padded to the ticks (gnuplot's autoscale), zero
  included for bars, never for a line that lives far from it.
- **Live**: a plot of an atom redraws on every change, without a
  re-plot call -- so a scan filling a surface, or an optimization's
  energy settling, shows itself as it runs.

## Shape of the code

- `clotran.plot` -- `graph` and the choice of picture: data in, a scene
  out (axes, ticks, marks, labels), as plain Clojure data.
- `clotran.plot.ticks`, `.sample`, `.time` -- the algorithms above, pure
  functions, each with its oracle.
- `clotran.plot.svg` -- a scene to SVG (and PNG): figures for papers, and
  the way tests look at output. Needs no window, so it never builds
  clotran-ui's native part.
- `clotran.plot.window` -- a scene in a clotran-ui canvas, with trace and
  zoom; 3D scenes in its 3D view.

## Milestones

1. Ticks: extended Wilkinson, agreeing with R's `labeling::extended`
   over a fuzzed set of ranges.
2. `(graph (* x x))` to SVG, then in a clotran-ui window, with trace and
   zoom -- the one for James's son.
3. Several functions; then data of each shape in the table.
4. Time axes.
5. Live: a plot of an atom.
6. 3D: a surface with a colour map; ball-and-stick molecules (bonds from
   covalent radii, CPK colours, as Avogadro and Jmol do).
7. The PES demo end to end: a scan run through a Fortran package fills a
   surface as it goes; a click on a point plays that point's
   optimization as an animated molecule beside its energy curve.
