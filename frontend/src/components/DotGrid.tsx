/**
 * Andaneri's own pattern: hand-painted dots of syrup colour in a even grid, the way they sit on the
 * brand's sheets. Drawn rather than shipped as an image, so it stays sharp on any screen, fills any
 * size without cutting a dot in half, and weighs a few hundred bytes.
 *
 * One tile of seven rows by seven dots is painted once and repeated - that is what the printed pattern
 * does too. The watercolour look is two things: an edge roughened by turbulence, and a lighter spot
 * up and to the left where the brush left less pigment.
 */

/** The syrup palette: berries, citrus, caramel and the herby greens. */
const ROWS: string[][] = [
  ['#ef5f62', '#ee5a72', '#f2dd6e', '#f0906e', '#4ea45c', '#ef85b5', '#9cbf4e'],
  ['#a52a3a', '#ee6a3c', '#3f9b52', '#f0a13a', '#8a5a2b', '#f0b43a', '#9a6630'],
  ['#c08a3e', '#f0906e', '#77c06a', '#f2dd6e', '#4ea45c', '#f5e07a', '#c08a3e'],
  ['#8a5a2b', '#4a5aa8', '#ee5a72', '#f0a13a', '#3f86c9', '#f0906e', '#3f86c9'],
  ['#f2dd6e', '#9cbf4e', '#66c1ea', '#f0903a', '#8a5aa8', '#f0a13a', '#8a5aa8'],
  ['#c08a3e', '#ee6a3c', '#ef85b5', '#8a5a2b', '#3f9b52', '#b0303f', '#ef5f62'],
  ['#ef5f62', '#ee5a72', '#f2dd6e', '#f0906e', '#4ea45c', '#9cbf4e', '#ee5a72'],
]

const STEP = 68
const RADIUS = 14

/** {@code scale}: the whole pattern smaller or larger - a phone wants more, smaller dots across it. */
export function DotGrid({ className = '', opacity = 1, scale = 1 }: { className?: string; opacity?: number; scale?: number }) {
  const size = STEP * ROWS.length
  return (
    <svg className={className} aria-hidden focusable="false" width="100%" height="100%" style={{ opacity }}>
      <defs>
        <filter id="dot-edge" x="-30%" y="-30%" width="160%" height="160%">
          {/* A little unevenness at the rim: paint never stops on a perfect circle. */}
          <feTurbulence type="fractalNoise" baseFrequency="0.055" numOctaves="2" seed="7" result="noise" />
          <feDisplacementMap in="SourceGraphic" in2="noise" scale="3.2" xChannelSelector="R" yChannelSelector="G" />
        </filter>
        <pattern id="dot-tile" width={size} height={size} patternUnits="userSpaceOnUse" patternTransform={`scale(${scale})`}>
          <g filter="url(#dot-edge)">
            {ROWS.map((row, y) =>
              row.map((color, x) => (
                <g key={`${x}-${y}`}>
                  <circle cx={x * STEP + STEP / 2} cy={y * STEP + STEP / 2} r={RADIUS} fill={color} />
                  {/* Where the brush held less pigment. */}
                  <circle cx={x * STEP + STEP / 2 - 3.5} cy={y * STEP + STEP / 2 - 4} r={RADIUS * 0.52} fill="#fff" opacity="0.22" />
                </g>
              )),
            )}
          </g>
        </pattern>
      </defs>
      <rect width="100%" height="100%" fill="url(#dot-tile)" />
    </svg>
  )
}
