# Nimku AutoClicker: product goal

**Goal:** an auto clicker built for **Clash of Kings**. It runs the daily game chores by itself in a loop, recovers when the game gets stuck, and needs only a few minutes of setup.

The app is still a general auto clicker. Clash of Kings support is the main target, and new features are designed for it first.

## Tasks to automate

| # | Task | What the bot does |
|---|------|-------------------|
| 1 | **Collect resources** | Goes to the city and taps the "ready" bubbles on the food, wood, iron and mithril buildings |
| 2 | **Alliance help** | Taps the alliance Help button whenever it shows up |
| 3 | **Train / heal / research** | When a queue is free: restarts troop training, heals the wounded and queues the next research |
| 4 | **Gather on world map** | Opens the map, searches for a resource tile, sends a march, and resends when marches come back |
| 5 | **Kill monsters** | Searches for a monster of the chosen level, attacks it and repeats while stamina lasts |

## How it runs: the task loop

The task loop was chosen because it is the simplest to use and suits game chores.

1. Pick the tasks you want, in order.
2. Press play. The bot runs task 1, then 2, 3, and so on, one after another.
3. After the last task it waits a configurable time (for example 5 minutes), then starts again.
4. It runs until you press stop, or until an optional time limit.

## Recovery: keeping the bot from getting stuck

All three protections are on by default:

- **Close popups:** offers, rewards and "X" dialogs are detected and closed. When nothing is recognized, the bot presses Back.
- **Restart the game:** if the screen does not change, or no task makes progress for N minutes, Clash of Kings is closed and reopened.
- **Notify:** a notification appears when the bot is stuck or a task keeps failing.

## Setup: guided capture

Game screenshots can't be shipped with the app: images depend on your screen size and language, and the game art belongs to its owner. Instead, a setup wizard walks you through each task:

> "Open the game and show the alliance **Help** button → tap **Capture** → draw a box around the button."

Once each button is captured, the task is ready. Captures can be exported and imported, so one setup can be shared between devices with the same resolution.

## Roadmap

| Phase | Content | Status |
|---|---|---|
| 0 | Base app: own app id, no ads or tracking, new icon, GitHub releases | In progress |
| 1 | **Clash of Kings task pack**: a ready-made smart scenario with all five tasks, the recovery events and guided image capture | Next |
| 2 | **Task loop**: run the tasks in order with a wait between rounds, plus per-task on/off switches | Planned |
| 3 | **Watchdog**: game restart when stuck, popup closer, notifications | Planned |
| 4 | Stats screen (resources collected, monsters killed, marches sent) and settings tuning | Later |

## Fair-use note

Automating a game can break its terms of service, and the game can ban accounts that use bots. Use this app at your own risk. It only taps the screen like a person would. It does not modify the game or read its memory.
