# myket-billing-client
[![Release](https://jitpack.io/v/myketstore/myket-billing-client.svg)](https://jitpack.io/#myketstore/myket-billing-client)

# Setup
Myket Billing Client is available in the Jitpack, so getting it as simple as doing this steps:

- Step 1. add the JitPack maven repository to `settings.gradle.kts` file under `dependencyResolutionManagement` list:

```gradle
maven { url = uri("https://jitpack.io") }
```


- Step 2. add our dependency to `build.gradle.kts` file under `dependencies` list:

```gradle
implementation("com.github.myketstore:myket-billing-client:{latest-version}")
```

- Step 3. Sync Gradle files with `Ctrl+Shift+O` keyboard shortcat in Android-Studio
    - or Click in main menu:`File > Sync Project With Gradle files`
