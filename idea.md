Build a complete simple Android app called "Checkpoint Timer".

Goal:
A countdown timer app where the user can define a total duration and multiple elapsed-time checkpoint alerts before the end. At each checkpoint and at the final end, the app plays a sound and optionally vibrates / shows a notification. The default checkpoint sound is a beep. The checkpoint sound and the final end sound are customizable. The user can save timer configurations as templates and quickly start them later.

Tech requirements:
- Kotlin
- Jetpack Compose
- Material 3
- Navigation Compose
- ViewModel + StateFlow
- Room for persistence
- Coroutines
- Min SDK 26
- Target SDK 35
- Gradle Kotlin DSL
- No XML layouts unless required by AndroidManifest
- Package name: com.example.checkpointtimer

Core features:

1. Template list screen
- Shows saved timer templates.
- Each template shows: name, total duration, number of checkpoints.
- FAB to create a new template.
- Tap a template to start it immediately.
- Long press or menu to edit, duplicate, delete.
- Quick switching between templates must be easy.

2. Template editor screen
- Fields:
  - Template name
  - Total duration: hours, minutes, seconds
  - End sound choice
  - End vibration toggle
- Checkpoint list:
  - Add checkpoint
  - Delete checkpoint
  - Reorder checkpoints
  - Each checkpoint has:
    - Label, e.g. "5 min checkpoint"
    - Trigger time: hours, minutes, seconds elapsed from start
    - Sound choice
    - Vibration toggle
    - Enabled toggle
- Validation:
  - Total duration must be greater than 0.
  - Checkpoints must be greater than 0.
  - Checkpoint trigger time must be less than total duration.
  - Prevent duplicate checkpoint trigger times if possible.
- Save button persists the template.
- Cancel button discards changes.

3. Timer screen
- Shows:
  - Template name
  - Large remaining time
  - Circular progress indicator
  - Total duration
  - Next checkpoint and time until it
  - Pause / Resume button
  - Reset button
  - Cancel / Stop button
- At each checkpoint:
  - Play the checkpoint sound. Default is a beep.
  - Vibrate if enabled.
  - Show a notification.
  - Briefly show a visual alert in the app if it is open.
- At the end:
  - Play the end sound.
  - Vibrate if enabled.
  - Show a final notification.
  - Show "Time's up" state.
- The timer must continue running when the app is in the background or the screen is off.
- Use a foreground service for the running timer.
- The foreground notification should show remaining time and include Pause/Resume and Cancel actions.
- Use SystemClock.elapsedRealtime() for accurate timing, not wall-clock time.
- Handle pause and resume correctly by adjusting elapsed time.
- Acquire a partial WakeLock while the timer is running.

4. Sound and vibration
- Default checkpoint sound must be a beep. Use ToneGenerator for the beep.
- The checkpoint sound must be customizable per checkpoint.
- The final end sound must be customizable separately from checkpoint sounds.
- Sound options should include:
  - Beep
  - Default alarm sound
  - Default notification sound
  - Silent
  - A system ringtone / notification sound chosen by the user via RingtoneManager
- Store the chosen sound reference in Room so it persists.
- Let the user choose a sound per checkpoint and a different sound for the end.
- Use Vibrator / VibratorManager for vibration.
- Request VIBRATE permission.
- For Android 13+, request POST_NOTIFICATIONS permission.
- For Android 14+, declare the foreground service correctly, using foregroundServiceType="specialUse" and FOREGROUND_SERVICE_SPECIAL_USE if needed, with an appropriate special-use subtype such as "timer".

5. Persistence
- Use Room to store templates and checkpoints.
- Templates must survive app restarts.
- Include sample seed data on first launch:
  - "10 min with 5 and 8 min checkpoints"
  - "Pomodoro 25 min with checkpoint at 20 min"
  - "5 min simple timer"

6. Architecture
- Use a clean but simple structure:
  - data: Room entities, DAOs, repository
  - timer: TimerService, TimerEngine, TimerState
  - ui: screens, components, theme
  - viewmodel: TemplateListViewModel, TemplateEditorViewModel, TimerViewModel
- Keep the code readable and not over-engineered.
- Add comments only where logic is non-obvious.

7. Edge cases
- If the user pauses, checkpoints should not fire while paused.
- If the user resumes, remaining checkpoint timing must be recalculated correctly.
- If a checkpoint time is reached while the app is in the background, it must still fire.
- If the timer is cancelled, stop all sounds, vibration, notifications, and the foreground service.
- If the user changes the system time, the timer must not be affected. Use elapsedRealtime.
- If a checkpoint and the end time are very close, avoid overlapping sounds if possible.

8. Tests
- Add unit tests for timer logic:
  - Checkpoints fire once and in order.
  - Pause/resume preserves correct remaining time.
  - End fires after all earlier checkpoints.
- Add basic Compose UI tests if practical.

9. Deliverables
- Full Android project files.
- README with build and run instructions.
- Gradle wrapper.
- Example templates seeded.
- After implementation, run ./gradlew assembleDebug and fix any build errors.

UI style:
- Simple, clean, dark/light theme support.
- Large readable numbers.
- Buttons should be obvious.
- Avoid complex onboarding. The app should open to the template list.

Ask clarifying questions if necessary. Make reasonable assumptions and generate the complete project.
