# kotlinjs-useanybrowser

Gradle plugin for using any available browser when testing JS under Kotlin Multiplatform.

By default, the JS target requires setting browsers under test explicitly, which indirectly requires
contributors (and CI) to agree about which browser(s) to use under testing, and have them installed.
The higher the number of contributors, the more cumbersome this becomes.

In some projects, it is enough to run tests on _any_ available browser.

## Usage

```kotlin
plugins {
    id("com.goncalossilva.useanybrowser") version "<version>"
}

kotlin {
    js {
        browser {
            testTask {
                useKarma {
                    useAnyBrowser()
                }
            }
        }
    }
}
```

After this, browser tests will run on any available browser, preferring headless variants first,
and Chrome variants second.

## License

Released under the [MIT License](https://opensource.org/licenses/MIT).
