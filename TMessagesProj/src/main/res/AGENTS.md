# Resources

- This is the main Android resource tree.
- Base lawX strings belong in `values/strings_lawx.xml`. Localized `values-*/strings_lawx.xml` files are Crowdin-managed.
- Keep density and qualifier variants consistent when changing icons, drawables, or layouts.
- `raw/` contains JSON, GLSL, audio, SVG, JS, and other code-adjacent assets; do not rename or move files without checking references.
- Put variant-only overrides in `src/debug/res` or other variant source sets, not by duplicating main resources.
- Run a debug build after resource edits to catch merge and reference issues.
