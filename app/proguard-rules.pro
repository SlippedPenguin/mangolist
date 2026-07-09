# Default Android optimize rules are prepended automatically.
# Project-specific rules below.

# Keep Apollo GraphQL runtime + generated classes — Apollo uses reflection
# for call-site resolution when minified.
-keep class com.apollographql.** { *; }
-keep class com.slippedpenguin.mangolist.graphql.** { *; }

# Room generates code; keep it intact.
-keep class * extends androidx.room.RoomDatabase { *; }

# kotlinx.serialization needs companion serializer accessors.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
