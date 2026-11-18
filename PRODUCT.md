# Pocket Audit

<!-- impeccable:product-schema 1 -->

## Platform

android

## Stack

Delegated by the user: Kotlin, Jetpack Compose, Room, CameraX, and bundled ML Kit barcode scanning. The user asked for a useful Android or Flutter portfolio project and authorized implementation; the native stack was selected to show direct Android integration.

## Users

Working assumption: a person checking equipment in a small workshop, classroom, or shared storage room. They have a CSV list, may have poor connectivity, and need to record what they actually found.

## Product Purpose

Import an expected inventory, scan or type item codes on site, record each item's condition and an optional photo, then export a report of checked, damaged, unexpected, and unobserved items. The reviewer of this portfolio should be able to run the same flow with fictional sample data.

## Operating Context

Phone use, often one-handed, while moving between shelves or rooms. Camera access can be denied or unavailable. The app must still allow manual code entry. An audit is local to one device and can be completed or reopened.

## Capabilities and Constraints

- Work without an account, network permission, or remote service.
- Import UTF-8 CSV with `code`, `name`, and `location` columns. Reject empty and duplicate codes with a useful error.
- Keep expected items and observations in Room. Importing a new inventory replaces the current audit only after confirmation.
- Read QR and common retail barcodes on device; manual entry is always available.
- Keep a repeated scan tied to the existing observation instead of creating a duplicate.
- Export a ZIP containing a CSV report, any attached photos, and a checksum manifest. Hashes detect changes inside the archive, but do not establish who created it.
- A missing item is reported only after the user explicitly completes the audit. Before that it is `not checked`.

## Evidence on Hand

No customer data or usage claims. The project ships with fictional inventory rows and automated tests. Screenshots must be captured from the built app on an Android emulator.

## Product Principles

- Make the next scan the easiest action.
- Never silently lose an earlier observation.
- Distinguish an unfinished check from a missing item.
- Make local data ownership and export behavior clear.
