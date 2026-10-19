# Household Hub client

## Tests
- Name every test `` `GIVEN <situation> WHEN <action> THEN <outcome>` ``, in household language rather than class or method names. The name carries the structure: the body has no `// GIVEN`, `// WHEN` or `// THEN` comments.
- No commas in a backticked name: Kotlin/Native rejects them, and `commonTest` compiles for iOS too, so a green `jvmTest` proves nothing. `TestNameCompatibilityTest` catches it.
- Android instrumented tests (`androidTest`, `androidDeviceTest`) use underscores, `GIVEN_x_WHEN_y_THEN_z`: a space isn't legal dex below API 30, and minSdk is 26.
- Unit and ViewModel tests do one action on the subject. A test that needs two actions is two tests.
- Screen and component tests go through a robot: one `<Screen>Robot` per screen or component, reached with `on<Screen> { … }` inside `runScreenTest`, with only the hub faked. Robot methods are what a person does or sees (`removes`, `seesMemory`, `cannotRemove`), and on-screen text comes from the string resources, never from copied literals. A robot script may chain actions and checks.
