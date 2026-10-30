# AutoEat + AutoTotem (Fabric 1.21.11, Java 21)
Build: install Gradle once and run `gradle wrapper --gradle-version 9.2.0`, then `./gradlew build`
(or just open the folder in IntelliJ). Jar: build/libs/autoeattotem-1.0.0.jar
Needs Fabric API; Mod Menu optional (config: Mod Menu -> AutoEat + AutoTotem, or the "Open config" keybind).
If a version in gradle.properties is rejected, use the current one from fabricmc.net/develop.
No packets are built by hand: keys are pressed through KeyBinding state, clicks go through HandledScreen.mouseClicked/mouseReleased.
