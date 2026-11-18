# Design notes

Pocket Audit is a field tool. Its visual language is closer to an equipment log than a shopping app: clear identifiers, large touch targets, restrained color, and visible audit state. Material 3 supplies navigation, controls, typography, and system behavior.

## Structure

- Compact phones use a bottom navigation bar for Audit, Inventory, and Report. The scan action is the single FAB on Audit and Inventory.
- Wide windows use a navigation rail. Lists remain readable rather than stretching across the full width.
- Scan is a focused full-screen task with live camera preview and an equally usable manual code field.
- Item detail presents identity first, then condition, note, photo, and Save.

## Color and type

- Light theme: warm paper surfaces, dark ink, deep teal for primary actions, amber for attention, and red only for errors or damaged status.
- Dark theme uses the same semantic roles with comfortable contrast. Dynamic color is allowed on Android 12 and newer; fixed fallbacks remain available.
- Use Material 3 type roles and Roboto. Codes and counts use tabular numerals where the platform font supports them.

## Behavior

- Audit progress names completed observations and remaining expected items. It does not call unvisited items missing until completion.
- Known, unexpected, damaged, and missing states have text labels in addition to color.
- Camera permission denial keeps manual entry available.
- CSV failures identify the row or field. Export failures keep the current audit intact.
- Confirm before replacing an imported inventory or resetting to the fictional sample.

## Accessibility

All controls have 48 dp targets, content descriptions for icon-only actions, and readable labels at larger font scale. System Back closes Scan or Item detail. Screen content observes system bars and keyboard insets.
