package jp.linkserver.nittcsc.update

/**
 * Run on a disposable emulator with the fixtures and permissions required by each test.
 * Build the debug APK and its test APK with `-PgoldyVerification=true`, then select the class
 * or method with `adb shell am instrument -e class`. Use a separate invocation for every
 * stage of a process-restart test, applying the documented permission changes between stages.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class DedicatedUpdateTest
