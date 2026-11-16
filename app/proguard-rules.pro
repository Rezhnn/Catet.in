# Catet.in keeps its model untouched: kotlinx.serialization and Room are handled
# by their own consumer rules. This file is intentionally small.
-keepclassmembers class com.catetin.app.backup.** {
    <fields>;
}
