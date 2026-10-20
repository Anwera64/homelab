#!/usr/bin/env bash
#
# make_app_icon.sh — regenerates the iOS app icons from tools/hearth_app_icon.svg: AppIcon-1024.png
# for the household's app, and AppIcon-Dev-1024.png for the dev app (Debug builds), which is the same
# artwork on Hearth's tertiary (#C05638) instead of its primary, the same as Android's debug icon.
#
# Xcode's asset catalog compiler accepts vector artwork (SVG) for ordinary image sets — that's
# what HearthLaunchTile.imageset uses — but NOT for an AppIcon set, which must be a raster image.
# tools/hearth_app_icon.svg is the canonical vector source (its glyph <path> `d` values are
# byte-identical to HearthIcon.kt's Household entry, the same as both HearthLaunchTile SVGs), and
# this script rasterises it once, by hand, into the committed AppIcon-1024.png. CI never re-runs
# this: the PNG is checked in, the same way tools/sse_fixture.py is a checked-in tool rather than
# something the build invokes.
#
# This needs librsvg's `rsvg-convert` on PATH — a DEVELOPER-MACHINE dependency only
# (`brew install librsvg`). It is not needed to build or test the app; nobody but whoever
# regenerates this one PNG needs it installed, and CI must never be made to need it.
#
# Apple rejects an app icon that carries an alpha channel outright — a fully opaque alpha channel
# is not good enough, the channel's mere presence is what's checked. So this script verifies the
# output PNG's IHDR colour-type byte (offset 25) is 2 (truecolour, no alpha) and refuses to leave
# a colour-type-6 (truecolour + alpha) file in place. As of librsvg 2.63.0, rsvg-convert already
# emits colour type 2 for this particular SVG, because hearth_app_icon.svg's own background <rect>
# covers the canvas edge to edge with no gaps — verified by inspecting the output below, so no
# separate CoreGraphics flattening pass is included; adding one would do nothing. If a future edit
# to the SVG ever leaves a transparent pixel, this script fails loudly instead of silently shipping
# a PNG Apple would reject.
#
# Usage (from apps/household-hub/client):
#   tools/make_app_icon.sh

set -euo pipefail

cd "$(dirname "$0")/.."

SVG="tools/hearth_app_icon.svg"
ASSETS="iosApp/HouseholdHub/Assets.xcassets"

if ! command -v rsvg-convert >/dev/null 2>&1; then
  echo "error: rsvg-convert not found on PATH (brew install librsvg)" >&2
  exit 1
fi

# $1: the background colour, $2: the PNG to write. The SVG's own background <rect> is recoloured,
# so the glyph is the same in both icons.
render() {
  local background="$1" out="$2" svg
  svg="$(mktemp)"
  sed "s/fill=\"#3C6E4E\"/fill=\"$background\"/" "$SVG" > "$svg"
  rsvg-convert -w 1024 -h 1024 --background-color="$background" "$svg" -o "$out"
  rm -f "$svg"

  color_type=$(od -An -tu1 -j 25 -N 1 "$out" | tr -d ' ')
  if [ "$color_type" != "2" ]; then
    echo "error: $out has PNG colour type $color_type, expected 2 (truecolour, no alpha)." >&2
    echo "       rsvg-convert emitted an alpha channel here; a flattening pass (CoreGraphics with" >&2
    echo "       CGImageAlphaInfo.noneSkipLast + ImageIO) needs to be added before this script's" >&2
    echo "       output can be considered done." >&2
    rm -f "$out"
    exit 1
  fi
  echo "Wrote $out (colour type 2, truecolour, no alpha)"
}

render '#3C6E4E' "$ASSETS/AppIcon.appiconset/AppIcon-1024.png"
render '#C05638' "$ASSETS/AppIcon-Dev.appiconset/AppIcon-Dev-1024.png"
