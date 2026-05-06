# Extending the Retained Mobile Actions Module

This guide is for developers working on the inherited
`customtasks/mobileactions/` module that still exists in this repository.

Important context:

- it is not the default PocketOps diagnosis flow
- the default launcher is `PocketOpsActivity`
- the retained Gallery task shell is still in the codebase, but it is no longer
  the main product entry point

Use this guide when you intentionally want to extend the retained function-
calling / mobile-actions demo code for experiments or internal tooling.

## Scope of this guide

This guide covers source changes under:

- `Android/src/app/src/main/java/com/google/ai/edge/gallery/customtasks/mobileactions/Actions.kt`
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/customtasks/mobileactions/MobileActionsTools.kt`
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/customtasks/mobileactions/MobileActionsViewModel.kt`
- `Android/src/app/src/main/java/com/google/ai/edge/gallery/customtasks/mobileactions/MobileActionsTask.kt`

It does not automatically expose the feature in the default PocketOps launcher
path. If you want a new action to become part of the PocketOps production UI,
you also need additional navigation and product-surface wiring.

## 1. Define your action type

Add a new entry to the `ActionType` enum in `Actions.kt`, then create an
`Action` subclass that defines the function name and parameters.

```kotlin
enum class ActionType {
  // ... existing types
  ACTION_NEW_CUSTOM_FUNCTION,
}

class NewCustomAction(val param: String) : Action(
  type = ActionType.ACTION_NEW_CUSTOM_FUNCTION,
  icon = Icons.Outlined.Favorite,
  functionCallDetails = FunctionCallDetails(
    functionName = "newCustomFunction",
    parameters = listOf(Pair("param", param))
  )
)
```

## 2. Add the tool definition

In `MobileActionsTools.kt`, create a new function annotated with `@Tool` and
`@ToolParam`. The function should send the action back through the
`onFunctionCalled` callback.

```kotlin
class MobileActionsTools(val onFunctionCalled: (Action) -> Unit) : Toolset {
  // ... existing tools

  @Tool(description = "Description of what this function does")
  fun newCustomFunction(
    @ToolParam(description = "Description of the parameter") param: String
  ): Map<String, String> {
    onFunctionCalled(NewCustomAction(param = param))
    return mapOf("result" to "success")
  }
}
```

## 3. Implement the Android behavior

Handle the new action in `MobileActionsViewModel.kt`.

```kotlin
fun performAction(action: Action, context: Context): String {
  return when (action) {
    // ... existing actions
    is NewCustomAction -> handleNewCustomAction(context, action.param)
    else -> ""
  }
}

private fun handleNewCustomAction(context: Context, param: String): String {
  // Implement your Android logic here.
  return ""
}
```

## 4. Update the system prompt if needed

If the action requires extra context such as current time, device state, or UI
constraints, update `getSystemPrompt()` in `MobileActionsTask.kt`.

## 5. Build and install

Open `Android/src` in Android Studio, or build from the command line:

```shell
# Windows
cd Android/src
.\gradlew.bat installDebug

# macOS / Linux
cd Android/src
./gradlew installDebug
```

After installation, the app drawer entry is still `PocketOps`. This guide only
changes the retained module code; it does not create a separate launcher icon
for the old Gallery shell.

## 6. Sanity checks

Before treating the feature as production-ready, verify:

- the action is reachable from the intended retained task flow
- required Android permissions are already handled
- the new action degrades safely if invoked on unsupported devices
- the PocketOps mainline is not accidentally depending on retained Gallery-only
  code paths
