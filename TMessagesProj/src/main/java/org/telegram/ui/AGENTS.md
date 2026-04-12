# org.telegram.ui

- This is the screen and navigation layer plus shared UI building blocks.
- Reuse existing `ActionBar`, `Cells`, `Components`, `LayoutHelper`, `Theme`, `UItem`, and adapter patterns before adding new ones.
- Keep networking and business logic out of UI classes when possible.
- Prefer resource strings, themed colors, and existing drawables over hardcoded values.
- Shared component changes can affect many screens, so keep diffs surgical.
- For lawX-only UI, check whether the behavior belongs in `ru.llacker.lawxgram` instead.
