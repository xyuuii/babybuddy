# Fonts

babybuddy supports MiSans as an optional local UI font, but the source repository does not redistribute MiSans font binaries.

## Why the Fonts Are Not Committed

MiSans is provided by Xiaomi under its own font license. The official license allows using MiSans in created works such as apps, but it restricts redistributing the font software itself. For that reason, raw `misans_*.ttf` files are intentionally ignored by Git.

Official MiSans page: <https://hyperos.mi.com/font/en/download/>

## Optional Local Setup

If you accept Xiaomi's MiSans license and want local builds to use MiSans, place these files in `app/src/main/res/font/`:

```text
misans_regular.ttf
misans_medium.ttf
misans_semibold.ttf
misans_bold.ttf
```

The app detects those resources at runtime. If they are absent, babybuddy falls back to the default Android font family so a fresh clone can still build successfully.
