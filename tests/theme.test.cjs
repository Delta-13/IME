const assert = require('node:assert/strict');
const {readFileSync} = require('node:fs');
const {test} = require('node:test');
const palettes = require('../theme/palettes.json');

// WCAG relative luminance, with opaque text and surface colors.
const luminance = hex => {
  const rgb = hex.slice(1).match(/../g).map(value => parseInt(value, 16) / 255);
  const linear = rgb.map(value => value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4);
  return linear[0] * 0.2126 + linear[1] * 0.7152 + linear[2] * 0.0722;
};
const contrast = (a, b) => {
  const values = [luminance(a), luminance(b)].sort((a, b) => b - a);
  return (values[0] + 0.05) / (values[1] + 0.05);
};
const pairs = (mode, colors, foregrounds, backgrounds, minimum) => {
  for (const foreground of foregrounds) for (const background of backgrounds) {
    const ratio = contrast(colors[foreground], colors[background]);
    assert.ok(ratio >= minimum,
      `${mode}: ${foreground} on ${background} = ${ratio.toFixed(2)}; expected >= ${minimum}`);
  }
};

for (const [mode, colors] of Object.entries(palettes)) {
  test(`${mode}: all small text, placeholders and pressed states meet 4.5:1`, () => {
    pairs(mode, colors, ['ink', 'muted', 'accent'],
      ['background', 'surface', 'surfaceSoft', 'accentSoft', 'pressed'], 4.5);
    pairs(mode, colors, ['onAccent'], ['accentFill', 'accentDark'], 4.5);
    pairs(mode, colors, ['ink'], ['selection'], 4.5);
    pairs(mode, colors, ['success'], ['successSoft', 'background', 'surface'], 4.5);
    pairs(mode, colors, ['warning'], ['warningSoft', 'surface'], 4.5);
    pairs(mode, colors, ['disabledText'], ['disabled'], 4.5);
  });
  test(`${mode}: interactive boundaries and slider indicators meet 3:1`, () => {
    pairs(mode, colors, ['controlBorder', 'accent'], ['surface', 'surfaceSoft'], 3);
  });
  test(`${mode}: native overlay colors match the React Native palette`, () => {
    const xml = readFileSync(`android/app/src/main/res/values${mode === 'dark' ? '-night' : ''}/colors.xml`, 'utf8');
    const native = Object.fromEntries([...xml.matchAll(/<color name="([^"]+)">(#[0-9A-F]{6})<\/color>/g)]
      .map(match => [match[1], match[2]]));
    for (const [name, value] of Object.entries(native)) {
      const key = name.replace('tone_', '').replace(/_([a-z])/g, (_, char) => char.toUpperCase());
      assert.equal(value, colors[key], name);
    }
    assert.deepEqual(Object.keys(native).sort(), [
      'tone_background', 'tone_surface', 'tone_surface_soft', 'tone_ink', 'tone_muted',
      'tone_line', 'tone_control_border', 'tone_accent', 'tone_accent_fill',
      'tone_accent_dark', 'tone_accent_soft', 'tone_on_accent',
    ].sort());
  });
}

test('theme changes do not recreate the activity or fade overlay text', () => {
  const manifest = readFileSync('android/app/src/main/AndroidManifest.xml', 'utf8');
  assert.match(manifest, /android:configChanges="[^"]*uiMode/);
  const overlayTheme = readFileSync('android/app/src/main/java/com/toneime/android/OverlayTheme.java', 'utf8');
  assert.match(overlayTheme, /root\.setAlpha\(1f\)/);
  assert.match(overlayTheme, /root\.setForceDarkAllowed\(false\)/);
  assert.match(overlayTheme, /getBackground\(\)\.mutate\(\)\.setAlpha/);
  const overlayLayout = readFileSync('android/app/src/main/res/layout/overlay_window.xml', 'utf8');
  assert.match(overlayLayout, /android:defaultFocusHighlightEnabled="false"/);
  const app = readFileSync('App.tsx', 'utf8');
  assert.doesNotMatch(app, /#[0-9A-Fa-f]{6}/, 'UI colors must come from the audited palettes');
  assert.doesNotMatch(app, /(?:pressed|disabled):\s*\{opacity:/);
});
