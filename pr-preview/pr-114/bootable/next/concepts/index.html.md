# Concepts

> The ideas the rest of the site builds on: Bootable's approach, the application's lifecycle, and the order services start in.

A few ideas run through every function’s page. Read these first, and the rest of the site makes sense.

**[No magic](https://rocketraman.github.io/bootable/bootable/next/concepts/philosophy.html.md)**

Bootable boots and runs your application, and nothing more: no scanning, no auto-configuration, no embedded DI, and typed config. What it deliberately leaves out, and why.

**[Lifecycle and services](https://rocketraman.github.io/bootable/bootable/next/concepts/lifecycle.html.md)**

An application is a set of services, which lifecycle controllers start and stop. How `boot` builds and runs it, its states, what happens when a start fails, and how it shuts down.

**[Start order](https://rocketraman.github.io/bootable/bootable/next/concepts/start-order.html.md)**

The order the services start in: phases, milestones within a phase, the tiebreaker for the rest, and how an application changes the order.
